/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.api.java.operators;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.flink.api.common.InvalidProgramException;
import org.apache.flink.api.common.typeinfo.AtomicType;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.CompositeType;
import org.apache.flink.api.common.typeutils.CompositeType.FlatFieldDescriptor;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.typeutils.TupleTypeInfo;
import org.apache.flink.api.java.typeutils.TupleTypeInfoBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;


public abstract class Keys<T> {
	private static final Logger LOG = LoggerFactory.getLogger(Keys.class);

	public abstract int getNumberOfKeyFields();

	public boolean isEmpty() {
		return getNumberOfKeyFields() == 0;
	}
	
	/**
	 * Check if two sets of keys are compatible to each other (matching types, key counts)
	 */
	public abstract boolean areCompatible(Keys<?> other) throws IncompatibleKeysException;
	
	public abstract int[] computeLogicalKeyPositions();
	
	
	// --------------------------------------------------------------------------------------------
	//  Specializations for expression-based / extractor-based grouping
	// --------------------------------------------------------------------------------------------
	
	
	public static class SelectorFunctionKeys<T, K> extends Keys<T> {

		private final KeySelector<T, K> keyExtractor;
		private final TypeInformation<K> keyType;
		private final int[] logicalKeyFields;

		public SelectorFunctionKeys(KeySelector<T, K> keyExtractor, TypeInformation<T> inputType, TypeInformation<K> keyType) {
			if (keyExtractor == null) {
				throw new NullPointerException("Key extractor must not be null.");
			}

			this.keyExtractor = keyExtractor;
			this.keyType = keyType;
			
			// we have to handle a special case here:
			// if the keyType is a tuple type, we need to select the full tuple with all its fields.
			if(keyType.isTupleType()) {
				ExpressionKeys<K> ek = new ExpressionKeys<K>(new String[] {ExpressionKeys.SELECT_ALL_CHAR}, keyType);
				logicalKeyFields = ek.computeLogicalKeyPositions();
			} else {
				logicalKeyFields = new int[] {0};
			}

			if (!this.keyType.isKeyType()) {
				throw new IllegalArgumentException("Invalid type of KeySelector keys");
			}
		}

		public TypeInformation<K> getKeyType() {
			return keyType;
		}

		public KeySelector<T, K> getKeyExtractor() {
			return keyExtractor;
		}

		@Override
		public int getNumberOfKeyFields() {
			return logicalKeyFields.length;
		}

		@Override
		public boolean areCompatible(Keys<?> other) throws IncompatibleKeysException {
			
			if (other instanceof SelectorFunctionKeys) {
				@SuppressWarnings("unchecked")
				SelectorFunctionKeys<?, K> sfk = (SelectorFunctionKeys<?, K>) other;

				return sfk.keyType.equals(this.keyType);
			}
			else if (other instanceof ExpressionKeys) {
				ExpressionKeys<?> expressionKeys = (ExpressionKeys<?>) other;
				
				if(keyType.isTupleType()) {
					// special case again:
					TupleTypeInfo<?> tupleKeyType = (TupleTypeInfo<?>) keyType;
					List<FlatFieldDescriptor> keyTypeFields = new ArrayList<FlatFieldDescriptor>(tupleKeyType.getTotalFields());
					tupleKeyType.getKey(ExpressionKeys.SELECT_ALL_CHAR, 0, keyTypeFields);
					if(expressionKeys.keyFields.size() != keyTypeFields.size()) {
						throw new IncompatibleKeysException(IncompatibleKeysException.SIZE_MISMATCH_MESSAGE);
					}
					for(int i=0; i < expressionKeys.keyFields.size(); i++) {
						if(!expressionKeys.keyFields.get(i).getType().equals(keyTypeFields.get(i).getType())) {
							throw new IncompatibleKeysException(expressionKeys.keyFields.get(i).getType(), keyTypeFields.get(i).getType() );
						}
					}
					return true;
				}
				if(expressionKeys.getNumberOfKeyFields() != 1) {
					throw new IncompatibleKeysException("Key selector functions are only compatible to one key");
				}
				
				if(expressionKeys.keyFields.get(0).getType().equals(this.keyType)) {
					return true;
				} else {
					throw new IncompatibleKeysException(expressionKeys.keyFields.get(0).getType(), this.keyType);
				}
			} else {
				throw new IncompatibleKeysException("The key is not compatible with "+other);
			}
		}

		@Override
		public int[] computeLogicalKeyPositions() {
			return logicalKeyFields;
		}

		@Override
		public String toString() {
			return "Key function (Type: " + keyType + ")";
		}
	}
	
	
	/**
	 * Represents (nested) field access through string and integer-based keys for Composite Types (Tuple or Pojo)
	 */
	public static class ExpressionKeys<T> extends Keys<T> {
		
		public static final String SELECT_ALL_CHAR = "*";
		
		/**
		 * Flattened fields representing keys fields
		 */
		private List<FlatFieldDescriptor> keyFields;
		
		/**
		 * two constructors for field-based (tuple-type) keys
		 */
		public ExpressionKeys(int[] groupingFields, TypeInformation<T> type) {
			this(groupingFields, type, false);
		}

		// int-defined field
		public ExpressionKeys(int[] groupingFields, TypeInformation<T> type, boolean allowEmpty) {
			if (!type.isTupleType()) {
				throw new InvalidProgramException("Specifying keys via field positions is only valid" +
						"for tuple data types. Type: " + type);
			}

			if (!allowEmpty && (groupingFields == null || groupingFields.length == 0)) {
				throw new IllegalArgumentException("The grouping fields must not be empty.");
			}
			// select all fields. Therefore, set all fields on this tuple level and let the logic handle the rest
			// (makes type assignment easier).
			if (groupingFields == null || groupingFields.length == 0) {
				groupingFields = new int[type.getArity()];
				for (int i = 0; i < groupingFields.length; i++) {
					groupingFields[i] = i;
				}
			} else {
				groupingFields = rangeCheckFields(groupingFields, type.getArity() -1);
			}
			TupleTypeInfoBase<?> tupleType = (TupleTypeInfoBase<?>)type;
			Preconditions.checkArgument(groupingFields.length > 0, "Grouping fields can not be empty at this point");
			
			keyFields = new ArrayList<FlatFieldDescriptor>(type.getTotalFields());
			// for each key, find the field:
			for(int j = 0; j < groupingFields.length; j++) {
				for(int i = 0; i < type.getArity(); i++) {
					TypeInformation<?> fieldType = tupleType.getTypeAt(i);
					
					if(groupingFields[j] == i) { // check if user set the key
						int keyId = countNestedElementsBefore(tupleType, i) + i;
						if(fieldType instanceof TupleTypeInfoBase) {
							TupleTypeInfoBase<?> tupleFieldType = (TupleTypeInfoBase<?>) fieldType;
							tupleFieldType.addAllFields(keyId, keyFields);
						} else {
							Preconditions.checkArgument(fieldType instanceof AtomicType, "Wrong field type");
							keyFields.add(new FlatFieldDescriptor(keyId, fieldType));
						}
						
					}
				}
			}
			keyFields = removeNullElementsFromList(keyFields);
		}
		
		private static int countNestedElementsBefore(TupleTypeInfoBase<?> tupleType, int pos) {
			if( pos == 0) {
				return 0;
			}
			int ret = 0;
			for (int i = 0; i < pos; i++) {
				TypeInformation<?> fieldType = tupleType.getTypeAt(i);
				ret += fieldType.getTotalFields() -1;
			}
			return ret;
		}
		
		public static <R> List<R> removeNullElementsFromList(List<R> in) {
			List<R> elements = new ArrayList<R>();
			for(R e: in) {
				if(e != null) {
					elements.add(e);
				}
			}
			return elements;
		}
		
		/**
		 * Create ExpressionKeys from String-expressions
		 */
		public ExpressionKeys(String[] expressionsIn, TypeInformation<T> type) {
			if(!(type instanceof CompositeType<?>)) {
				throw new IllegalArgumentException("Type "+type+" is not a composite type. Key expressions are not supported.");
			}
			CompositeType<T> cType = (CompositeType<T>) type;
			
			String[] expressions = removeDuplicates(expressionsIn);
			if(expressionsIn.length != expressions.length) {
				LOG.warn("The key expressions contained duplicates. They are now unique");
			}
			// extract the keys on their flat position
			keyFields = new ArrayList<FlatFieldDescriptor>(expressions.length);
			for (int i = 0; i < expressions.length; i++) {
				List<FlatFieldDescriptor> keys = new ArrayList<FlatFieldDescriptor>(); // use separate list to do a size check
				cType.getKey(expressions[i], 0, keys);
				if(keys.size() == 0) {
					throw new IllegalArgumentException("Unable to extract key from expression "+expressions[i]+" on key "+cType);
				}
				keyFields.addAll(keys);
			}
		}
		
		@Override
		public int getNumberOfKeyFields() {
			if(keyFields == null) {
				return 0;
			}
			return keyFields.size();
		}

		@Override
		public boolean areCompatible(Keys<?> other) throws IncompatibleKeysException {

			if (other instanceof ExpressionKeys) {
				ExpressionKeys<?> oKey = (ExpressionKeys<?>) other;

				if(oKey.getNumberOfKeyFields() != this.getNumberOfKeyFields() ) {
					throw new IncompatibleKeysException(IncompatibleKeysException.SIZE_MISMATCH_MESSAGE);
				}
				for(int i=0; i < this.keyFields.size(); i++) {
					if(!this.keyFields.get(i).getType().equals(oKey.keyFields.get(i).getType())) {
						throw new IncompatibleKeysException(this.keyFields.get(i).getType(), oKey.keyFields.get(i).getType() );
					}
				}
				return true;
			} else if(other instanceof SelectorFunctionKeys<?, ?>) {
				return other.areCompatible(this);
			} else {
				throw new IncompatibleKeysException("The key is not compatible with "+other);
			}
		}

		@Override
		public int[] computeLogicalKeyPositions() {
			List<Integer> logicalKeys = new LinkedList<Integer>();
			for(FlatFieldDescriptor kd : keyFields) {
				logicalKeys.addAll( Ints.asList(kd.getPosition()));
			}
			return Ints.toArray(logicalKeys);
		}
		
	}
	
	private static String[] removeDuplicates(String[] in) {
		List<String> ret = new LinkedList<String>();
		for(String el : in) {
			if(!ret.contains(el)) {
				ret.add(el);
			}
		}
		return ret.toArray(new String[ret.size()]);
	}
	// --------------------------------------------------------------------------------------------
	
	
	// --------------------------------------------------------------------------------------------
	//  Utilities
	// --------------------------------------------------------------------------------------------


	private static final int[] rangeCheckFields(int[] fields, int maxAllowedField) {

		// range check and duplicate eliminate
		int i = 1, k = 0;
		int last = fields[0];

		if (last < 0 || last > maxAllowedField) {
			throw new IllegalArgumentException("Tuple position is out of range.");
		}

		for (; i < fields.length; i++) {
			if (fields[i] < 0 || fields[i] > maxAllowedField) {
				throw new IllegalArgumentException("Tuple position is out of range.");
			}
			if (fields[i] != last) {
				k++;
				last = fields[i];
				fields[k] = fields[i];
			}
		}

		// check if we eliminated something
		if (k == fields.length - 1) {
			return fields;
		} else {
			return Arrays.copyOfRange(fields, 0, k+1);
		}
	}
	
	public static class IncompatibleKeysException extends Exception {
		private static final long serialVersionUID = 1L;
		public static final String SIZE_MISMATCH_MESSAGE = "The number of specified keys is different.";
		
		public IncompatibleKeysException(String message) {
			super(message);
		}

		public IncompatibleKeysException(TypeInformation<?> typeInformation, TypeInformation<?> typeInformation2) {
			super(typeInformation+" and "+typeInformation2+" are not compatible");
		}
	}
}
