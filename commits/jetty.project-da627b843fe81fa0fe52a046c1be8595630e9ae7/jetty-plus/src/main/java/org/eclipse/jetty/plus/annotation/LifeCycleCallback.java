// ========================================================================
// Copyright (c) 2006-2009 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Apache License v2.0 which accompanies this distribution.
// The Eclipse Public License is available at 
// http://www.eclipse.org/legal/epl-v10.html
// The Apache License v2.0 is available at
// http://www.opensource.org/licenses/apache2.0.php
// You may elect to redistribute this code under either of these licenses. 
// ========================================================================

package org.eclipse.jetty.plus.annotation;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.eclipse.jetty.util.IntrospectionUtil;



/**
 * LifeCycleCallback
 *
 *
 */
public abstract class LifeCycleCallback
{
    public static final Object[] __EMPTY_ARGS = new Object[] {};
    private Method _target;
    private Class _targetClass;
    
    
    public LifeCycleCallback()
    {
    }


    /**
     * @return the _targetClass
     */
    public Class getTargetClass()
    {
        return _targetClass;
    }


    /**
     * @param name the class to set
     */
    public void setTargetClass(Class clazz)
    {
        _targetClass = clazz;
    }
    
    
    /**
     * @return the target
     */
    public Method getTarget()
    {
        return _target;
    }

    /**
     * @param target the target to set
     */
    public void setTarget(Method target)
    {
        this._target = target;
    }
    
    
    

    public void setTarget (Class clazz, String methodName)
    {
        try
        {
            Method method = IntrospectionUtil.findMethod(clazz, methodName, null, true, true);
            validate(clazz, method);
            _target = method;
            _targetClass = clazz;
        }
        catch (NoSuchMethodException e)
        {
            throw new IllegalArgumentException ("Method "+methodName+" not found on class "+clazz.getName());
        }
    }


    
    
    public void callback (Object instance)
    throws Exception
    {
        if (getTarget() != null)
        {
            boolean accessibility = getTarget().isAccessible();
            getTarget().setAccessible(true);
            getTarget().invoke(instance, __EMPTY_ARGS);
            getTarget().setAccessible(accessibility);
        }
    }

    

    /**
     * Find a method of the given name either directly in the given
     * class, or inherited.
     * 
     * @param pack the package of the class under inspection
     * @param clazz the class under inspection
     * @param methodName the method to find 
     * @param checkInheritance false on first entry, true if a superclass is being introspected
     * @return
     */
    public Method findMethod (Package pack, Class clazz, String methodName, boolean checkInheritance)
    {
        if (clazz == null)
            return null;

        try
        {
            Method method = clazz.getDeclaredMethod(methodName, null);
            if (checkInheritance)
            {
                int modifiers = method.getModifiers();
                if (Modifier.isProtected(modifiers) || Modifier.isPublic(modifiers) || (!Modifier.isPrivate(modifiers)&&(pack.equals(clazz.getPackage()))))
                    return method;
                else
                    return findMethod(clazz.getPackage(), clazz.getSuperclass(), methodName, true);
            }
            return method;
        }
        catch (NoSuchMethodException e)
        {
            return findMethod(clazz.getPackage(), clazz.getSuperclass(), methodName, true);
        }
    }

    public boolean equals (Object o)
    {
        if (o==null)
            return false;
        if (!(o instanceof LifeCycleCallback))
            return false;
        LifeCycleCallback callback = (LifeCycleCallback)o;
        
        if (callback.getTargetClass()==null)
        {
            if (getTargetClass() != null)
                return false;
        }
        else if(!callback.getTargetClass().equals(getTargetClass()))
           return false;
        if (callback.getTarget()==null)
        {
            if (getTarget() != null)
                return false;
        }
        else if (!callback.getTarget().equals(getTarget()))
            return false;
        
        return true;
    }
    
    public abstract void validate (Class clazz, Method m);
}
