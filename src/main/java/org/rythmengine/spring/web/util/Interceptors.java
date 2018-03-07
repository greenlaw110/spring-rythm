package org.rythmengine.spring.web.util;

/*-
 * #%L
 * Spring Rythm Plugin
 * %%
 * Copyright (C) 2017 - 2018 OSGL (Open Source General Library)
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.osgl.util.C;
import org.osgl.util.ListBuilder;
import org.rythmengine.spring.web.RythmConfigurer;
import org.rythmengine.spring.web.result.Result;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Created by luog on 26/02/14.
 */
public class Interceptors extends WebMvcConfigurerAdapter {

    private static volatile C.List<HandlerInterceptor> interceptors;

    private static class Registry extends InterceptorRegistry {
        @Override
        public List<Object> getInterceptors() {
            return super.getInterceptors();
        }
    }

    public static C.List<HandlerInterceptor> getInterceptors() {
        if (null != interceptors) {
            return interceptors;
        }
        synchronized (Interceptors.class) {
            if (null != interceptors) {
                return interceptors;
            }
            ApplicationContext ctx = RythmConfigurer.getInstance().getApplicationContext();
            String[] sa = ctx.getBeanNamesForType(WebMvcConfigurer.class);
            int len = sa.length;
            Registry reg = new Registry();
            for (int i = 0; i < len; ++i) {
                WebMvcConfigurer conf = (WebMvcConfigurer)ctx.getBean(sa[i]);
                conf.addInterceptors(reg);
            }
            List<Object> lo = reg.getInterceptors();
            if (lo.isEmpty()) {
                interceptors = C.list();
                return interceptors;
            }
            ListBuilder<HandlerInterceptor> lb = new ListBuilder<HandlerInterceptor>(10);
            for (Object o : lo) {
                lb.add((HandlerInterceptor) o);
            }
            interceptors = lb.toList();
            return interceptors;
        }
    }

    public static void applyPostHandlers(HttpServletRequest req, HttpServletResponse res) {
        for (HandlerInterceptor in : getInterceptors().reverse()) {
            try {
                in.postHandle(req, res, null, null);
            } catch (Result r) {
                // ignore
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void applyCompleteHandlers(HttpServletRequest req, HttpServletResponse res) {
        for (HandlerInterceptor in : getInterceptors().reverse()) {
            try {
                in.afterCompletion(req, res, null, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

}
