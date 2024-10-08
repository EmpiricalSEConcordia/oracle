//$Id$
package org.hibernate.search.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declare a field as the document id. If set to a property, the property will be used
 * TODO: If set to a class, the class itself will be passed to the FieldBridge
 * Note that @{link org.hibernate.search.bridge.FieldBridge#get} must return the Entity id
 *
 * @author Emmanuel Bernard
 */
@Retention( RetentionPolicy.RUNTIME )
@Target( {ElementType.METHOD, ElementType.FIELD} )
@Documented
public @interface DocumentId {
	String name() default "";
}
