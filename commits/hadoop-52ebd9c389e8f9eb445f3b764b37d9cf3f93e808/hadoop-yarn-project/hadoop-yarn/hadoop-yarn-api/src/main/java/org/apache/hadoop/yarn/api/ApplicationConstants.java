/**
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

package org.apache.hadoop.yarn.api;

import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.util.Shell;

/**
 * This is the API for the applications comprising of constants that YARN sets
 * up for the applications and the containers.
 * 
 * TODO: Investigate the semantics and security of each cross-boundary refs.
 */
public interface ApplicationConstants {
  
  /**
   * The environment variable for APP_SUBMIT_TIME. Set in AppMaster environment
   * only
   */
  public static final String APP_SUBMIT_TIME_ENV = "APP_SUBMIT_TIME_ENV";

  public static final String CONTAINER_TOKEN_FILE_ENV_NAME =
      UserGroupInformation.HADOOP_TOKEN_FILE_LOCATION;

  /**
   * The environmental variable for APPLICATION_WEB_PROXY_BASE. Set in 
   * ApplicationMaster's environment only. This states that for all non-relative
   * web URLs in the app masters web UI what base should they have.
   */
  public static final String APPLICATION_WEB_PROXY_BASE_ENV = 
    "APPLICATION_WEB_PROXY_BASE";

  public static final String LOG_DIR_EXPANSION_VAR = "<LOG_DIR>";

  public static final String STDERR = "stderr";

  public static final String STDOUT = "stdout";

  /**
   * The environment variable for MAX_APP_ATTEMPTS. Set in AppMaster environment
   * only
   */
  public static final String MAX_APP_ATTEMPTS_ENV = "MAX_APP_ATTEMPTS";

  /**
   * Environment for Applications.
   * 
   * Some of the environment variables for applications are <em>final</em> 
   * i.e. they cannot be modified by the applications.
   */
  public enum Environment {
    /**
     * $USER
     * Final, non-modifiable.
     */
    USER(Shell.WINDOWS ? "USERNAME": "USER"),
    
    /**
     * $LOGNAME
     * Final, non-modifiable.
     */
    LOGNAME("LOGNAME"),
    
    /**
     * $HOME
     * Final, non-modifiable.
     */
    HOME("HOME"),
    
    /**
     * $PWD
     * Final, non-modifiable.
     */
    PWD("PWD"),
    
    /**
     * $PATH
     */
    PATH("PATH"),
    
    /**
     * $SHELL
     */
    SHELL("SHELL"),
    
    /**
     * $JAVA_HOME
     */
    JAVA_HOME("JAVA_HOME"),
    
    /**
     * $CLASSPATH
     */
    CLASSPATH("CLASSPATH"),
    
    /**
     * $APP_CLASSPATH
     */
    APP_CLASSPATH("APP_CLASSPATH"),
    
    /**
     * $LD_LIBRARY_PATH
     */
    LD_LIBRARY_PATH("LD_LIBRARY_PATH"),
    
    /**
     * $HADOOP_CONF_DIR
     * Final, non-modifiable.
     */
    HADOOP_CONF_DIR("HADOOP_CONF_DIR"),
    
    /**
     * $HADOOP_COMMON_HOME
     */
    HADOOP_COMMON_HOME("HADOOP_COMMON_HOME"),
    
    /**
     * $HADOOP_HDFS_HOME
     */
    HADOOP_HDFS_HOME("HADOOP_HDFS_HOME"),
    
    /**
     * $MALLOC_ARENA_MAX
     */
    MALLOC_ARENA_MAX("MALLOC_ARENA_MAX"),
    
    /**
     * $HADOOP_YARN_HOME
     */
    HADOOP_YARN_HOME("HADOOP_YARN_HOME"),

    /**
     * $CONTAINER_ID
     * Final, exported by NodeManager and non-modifiable by users.
     */
    CONTAINER_ID("CONTAINER_ID"),

    /**
     * $NM_HOST
     * Final, exported by NodeManager and non-modifiable by users.
     */
    NM_HOST("NM_HOST"),

    /**
     * $NM_HTTP_PORT
     * Final, exported by NodeManager and non-modifiable by users.
     */
    NM_HTTP_PORT("NM_HTTP_PORT"),

    /**
     * $NM_PORT
     * Final, exported by NodeManager and non-modifiable by users.
     */
    NM_PORT("NM_PORT"),

    /**
     * $LOCAL_DIRS
     * Final, exported by NodeManager and non-modifiable by users.
     */
    LOCAL_DIRS("LOCAL_DIRS"),

    /**
     * $LOG_DIRS
     * Final, exported by NodeManager and non-modifiable by users.
     * Comma separate list of directories that the container should use for
     * logging.
     */
    LOG_DIRS("LOG_DIRS");

    private final String variable;
    private Environment(String variable) {
      this.variable = variable;
    }
    
    public String key() {
      return variable;
    }
    
    public String toString() {
      return variable;
    }
    
    public String $() {
      if (Shell.WINDOWS) {
        return "%" + variable + "%";
      } else {
        return "$" + variable;
      }
    }
  }
}
