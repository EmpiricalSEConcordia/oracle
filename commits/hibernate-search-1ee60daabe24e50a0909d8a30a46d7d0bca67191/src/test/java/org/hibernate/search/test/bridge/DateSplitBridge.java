//$Id$
package org.hibernate.search.test.bridge;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.hibernate.search.bridge.FieldBridge;
import org.hibernate.search.bridge.LuceneOptions;

/**
 * Store the date in 3 different fields - year, month, day - to ease Range Query per
 * year, month or day (eg get all the elements of December for the last 5 years).
 * 
 * @author Emmanuel Bernard
 */
public class DateSplitBridge implements FieldBridge {
	private final static TimeZone GMT = TimeZone.getTimeZone("GMT");

	public void set(String name, Object value, Document document, LuceneOptions luceneOptions) {
		Date date = (Date) value;
		Calendar cal = GregorianCalendar.getInstance(GMT);
		cal.setTime(date);
		int year = cal.get(Calendar.YEAR);
		int month = cal.get(Calendar.MONTH) + 1;
		int day = cal.get(Calendar.DAY_OF_MONTH);
		
		// set year
		Field field = new Field(name + ".year", String.valueOf(year),
				luceneOptions.getStore(), luceneOptions.getIndex(),
				luceneOptions.getTermVector());
		field.setBoost(luceneOptions.getBoost());
		document.add(field);
		
		// set month and pad it if needed
		field = new Field(name + ".month", month < 10 ? "0" : ""
				+ String.valueOf(month), luceneOptions.getStore(),
				luceneOptions.getIndex(), luceneOptions.getTermVector());
		field.setBoost(luceneOptions.getBoost());
		document.add(field);
		
		// set day and pad it if needed
		field = new Field(name + ".day", day < 10 ? "0" : ""
				+ String.valueOf(day), luceneOptions.getStore(),
				luceneOptions.getIndex(), luceneOptions.getTermVector());
		field.setBoost(luceneOptions.getBoost());
		document.add(field);
	}
}
