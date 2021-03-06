/*
 * Copyright 1998-2010 Linux.org.ru
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package ru.org.linux.spring.boxlets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;

import ru.org.linux.spring.CacheableController;
import ru.org.linux.spring.commons.CacheProvider;

public abstract class SpringBoxlet extends AbstractController implements CacheableController{

  protected abstract ModelAndView getData(HttpServletRequest request
  ) throws Exception;

  
  @Override
  protected ModelAndView handleRequestInternal(HttpServletRequest request,
                                               HttpServletResponse response) throws Exception {
    ModelAndView mav = getData(request);
    if (mav == null){
      mav = new ModelAndView();
    }

    if (request.getParameterMap().containsKey("edit")){
      mav.addObject("editMode", Boolean.TRUE);
    }
    return mav;
  }

  protected <T> T getFromCache(CacheProvider cacheProvider, GetCommand<T> callback) throws Exception {
    return getFromCache(cacheProvider, getCacheKey(), callback);
  }

  protected <T> T getFromCache(CacheProvider cacheProvider, String key, GetCommand<T> callback) throws Exception {
    @SuppressWarnings("unchecked")
    T result = (T) cacheProvider.getFromCache(key);
    if (result == null){
       result = callback.get();
       cacheProvider.storeToCache(key, result, getExpiryTime());
    }
    
    return result;
  }

  public interface GetCommand<T>{
    T get() throws Exception;
  }

  @Override
  public String getCacheKey(){
    return getClass().getName();
  }

  @Override
  public int getExpiryTime(){
    return DEFAULT_EXPIRE;
  }
}
