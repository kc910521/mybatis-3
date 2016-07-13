/**
 *    Copyright 2009-2016 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.scripting.xmltags;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ognl.BooleanExpression;
import ognl.Ognl;
import ognl.OgnlException;

import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.type.NoRestriction;
import org.hsqldb.lib.HashMap;

/**
 * Caches OGNL parsed expressions.
 *
 * @author Eduardo Macarron
 *
 * @see <a href='http://code.google.com/p/mybatis/issues/detail?id=342'>Issue 342</a>
 */
public final class OgnlCache {
  
  private static boolean RESTRICT_MODE = false;
  
  private static final String REG_GET_PARAM = "(\\()(\\w+?)(\\s*)!=(\\s*)null(\\s*)\\)";

  private static final Map<String, Object> expressionCache = new ConcurrentHashMap<String, Object>();

  private OgnlCache() {
    // Prevent Instantiation of Static Class
  }

  public static Object getValue(String expression, Object root) {
    try {
      Map<Object, OgnlClassResolver> context = Ognl.createDefaultContext(root, new OgnlClassResolver());
      Ognl.addDefaultContext(root, context);
      Object node = parseExpression(expression);
      if (!RESTRICT_MODE){
    	  OgnlCache.fixMissItem(node,root);
      }
      return Ognl.getValue(node, context, root);
    } catch (OgnlException e) {
      throw new BuilderException("Error evaluating expression '" + expression + "'. Cause: " + e, e);
    }
  }

  private static Object parseExpression(String expression) throws OgnlException {
    Object node = expressionCache.get(expression);
    if (node == null) {
      node = Ognl.parseExpression(expression);
      expressionCache.put(expression, node);
    }
    return node;
  }
  
  /**
   * kc910521 :
   * when the Object (implements NoRestriction) you set on 'parameterType' of SQLxml but miss required field 
   * may cause Exception and interrupt before the change
   * 
   * now you can set all of them to null before Exception happening
   * 1 of USAGE :
   *        <if test="id != null or id != '' or name==null or sexual != null ">
        		AND us.id = #{id}may
        	<\/if>
   * in that case ,'id' and 'sexual' may be setting to null before executing by OGNL
   * if that not exist in 'parameterType' 
   * 
   * @param node
   * @param root
   */
  private static void fixMissItem(Object node,final Object root){
	  //ck 判断是否为动态上下文，如果是的话，预判参数是否能覆盖，不能的话，添加默认
	  DynamicContext.ContextMap dc = null;
	  if (root instanceof DynamicContext.ContextMap){
		  dc = (DynamicContext.ContextMap)root;
	  }
	  Object parObj = dc.get("_parameter");
	  System.out.println("ddddd:"+(node instanceof BooleanExpression));
	  if (dc != null && parObj != null 
			  && parObj instanceof NoRestriction && node instanceof BooleanExpression){
		  @SuppressWarnings("unchecked")
		Class<NoRestriction> userCla = (Class<NoRestriction>) parObj.getClass();
		  Field[] fields = userCla.getDeclaredFields();
		  Set<String> params = takeParamsNotNull((BooleanExpression)node);
		  for (String str : params){
			  if (!nameInFields(fields,str)){
				  dc.put(str, null);
			  }
		  }
		  //System.out.println(dc.containsKey("name"));
	  }
  }
  
  private static Set<String> takeParamsNotNull(BooleanExpression node){
	  Set<String> keys = new HashSet<String>();
	  Pattern pt = Pattern.compile(OgnlCache.REG_GET_PARAM);
      Matcher m = null;
      for (int idx = 0; idx < node.jjtGetNumChildren(); idx ++){
    	  System.out.println(node.jjtGetChild(idx).toString());
    	  m = pt.matcher(node.jjtGetChild(idx).toString());
          while(m.find()){
            	keys.add(m.group().replaceAll("(\\(|\\)|\\s|!=(\\s*)null)", ""));
          }
      }
	  return keys;
  }
  
  private static boolean nameInFields(Field[] fields, String name){
	  for (Field fd : fields){
		  if (fd.getName().equals(name)){
			  return true;
		  }
	  }
	  return false;
  }

}
