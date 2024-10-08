package org.mockito.internal.invocation;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.mockito.exceptions.base.MockitoException;

public class MockitoMethod implements Serializable {

  private static final long serialVersionUID = 6005610965006048445L;
  private Class<?> declaringClass;
  private String methodName;
  private Class<?>[] parameterTypes;
  private Class<?> returnType;

  public MockitoMethod(Method method) {
    declaringClass = method.getDeclaringClass();
    methodName = method.getName();
    parameterTypes = method.getParameterTypes();
    returnType = method.getReturnType();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((declaringClass == null) ? 0 : declaringClass.hashCode());
    result = prime * result + ((methodName == null) ? 0 : methodName.hashCode());
    result = prime * result + Arrays.hashCode(parameterTypes);
    result = prime * result + ((returnType == null) ? 0 : returnType.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    MockitoMethod other = (MockitoMethod) obj;
    if (declaringClass == null) {
      if (other.declaringClass != null)
        return false;
    } else if (!declaringClass.equals(other.declaringClass))
      return false;
    if (methodName == null) {
      if (other.methodName != null)
        return false;
    } else if (!methodName.equals(other.methodName))
      return false;
    if (!Arrays.equals(parameterTypes, other.parameterTypes))
      return false;
    if (returnType == null) {
      if (other.returnType != null)
        return false;
    } else if (!returnType.equals(other.returnType))
      return false;
    return true;
  }

  public Method getMethod() {
    try {
      return declaringClass.getDeclaredMethod(methodName, parameterTypes);
    } catch (SecurityException e) {
      // TODO real exception
      throw new MockitoException("could not create method", e);
    } catch (NoSuchMethodException e) {
      // TODO real exception
      throw new MockitoException("could not create method", e);
    }
  }

  public String getName() {
    return methodName;
  }

  public Class<?> getReturnType() {
    return returnType;
  }

}
