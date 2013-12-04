package org.rythmengine.spring.web.servlet.view;

import org.rythmengine.RythmEngine;
import org.rythmengine.exception.RythmException;
import org.rythmengine.extension.ICodeType;
import org.rythmengine.internal.compiler.TemplateClass;
import org.rythmengine.resource.ITemplateResource;
import org.rythmengine.resource.TemplateResourceManager;
import org.rythmengine.template.TemplateBase;
import org.rythmengine.utils.IO;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.servlet.view.AbstractTemplateView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 2/12/13
 * Time: 1:32 PM
 * To change this template use File | Settings | File Templates.
 */
public class RythmView extends AbstractTemplateView {

    private RythmEngine engine;

    private ITemplateResource rsrc;

    private TemplateClass tc;

    private ICodeType codeType;

    private boolean outputReqParams = false;

    private boolean underscoreImplicitVarNames = false;

    public void setRythmEngine(RythmEngine engine) {
        this.engine = engine;
    }

    protected RythmEngine getRythmEngine() {
        return this.engine;
    }

    @Override
    protected void initApplicationContext(ApplicationContext context) {
        super.initApplicationContext(context);
        RythmEngine engine = getRythmEngine();
        if (engine == null) {
            engine = autodetectRythmEngine();
            // No explicit RythmEngine: try to autodetect one.
            setRythmEngine(engine);
        }
        Object o = engine.getProperty(RythmConfigurer.CONF_OUTOUT_REQ_PARAMS);
        if (null != o) {
            try {
                outputReqParams = (Boolean) o;
            } catch (Exception e) {
                // ignore it
                logger.warn("error set output request parameter config", e);
            }
        }
        o = engine.getProperty(RythmConfigurer.CONF_UNDERSCORE_IMPLICIT_VAR_NAME);
        if (null != o) {
            try {
                underscoreImplicitVarNames = (Boolean) o;
            } catch (Exception e) {
                // ignore it
                logger.warn("error set underscore implicit variable name config", e);
            }
        }

        String url = getUrl();
        TemplateResourceManager rm = engine.resourceManager();
        rsrc = rm.getResource(url);
        if (null == rsrc || !rsrc.isValid()) {
            // try guess it is ".html" file
            rsrc = rm.getResource(url + ".html");
        }
    }

    protected RythmEngine autodetectRythmEngine() throws BeansException {
        try {
            RythmConfig config = BeanFactoryUtils.beanOfTypeIncludingAncestors(
                    getApplicationContext(), RythmConfig.class, true, false);
            return config.getRythmEngine();
        } catch (NoSuchBeanDefinitionException ex) {
            throw new ApplicationContextException(
                    "Must define a single RythmConfig bean in this web application context " +
                            "(may be inherited): RythmConfigurer is the usual implementation. " +
                            "This bean may be given any name.", ex);
        }
    }

    private RythmException re;

    @Override
    public boolean checkResource(Locale locale) throws Exception {
        if (null != tc) {
            return true;
        }
        if (!rsrc.isValid()) {
            return false;
        }
        try {
            tc = engine.getTemplateClass(rsrc);
            String fullName = tc.getTagName();
            engine.registerTemplate(fullName, tc.asTemplate(engine));
            codeType = rsrc.codeType(engine);
            re = null;
            return true;
        } catch (RythmException e) {
            if (engine.isDevMode()) {
                re = e;
                return true;
            }
            throw new ApplicationContextException(
                    "Failed to load rythm template for URL [" + getUrl() + "]", e);
        }
    }

    static final ThreadLocal<Map<String, Object>> renderArgs = new ThreadLocal<Map<String, Object>>();


    @Override
    protected void renderMergedTemplateModel(Map<String, Object> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
        RythmEngine engine = this.engine;
        if (null != re) {
            checkResource(null);
            engine.render(response.getOutputStream(), "errors/500.html", re);
            return;
        }

        Locale locale = LocaleContextHolder.getLocale();
        engine.prepare(locale);
        try {
            TemplateClass tc = this.tc;
            if (engine.mode().isDev()) {
                tc = engine.getTemplateClass(rsrc);
            }
            TemplateBase t = (TemplateBase) tc.asTemplate(engine);
            Map<String, Object> params = new HashMap<String, Object>();
            if (outputReqParams) {
                Map reqMap = request.getParameterMap();
                for (Object o : reqMap.keySet()) {
                    String k = o.toString();
                    String[] va = request.getParameterValues(k);
                    if (va.length == 1) {
                        params.put(k, va[0]);
                    } else if (va.length > 1) {
                        params.put(k, va);
                    }
                }
            }
            params.putAll(model);
            params.put(underscoreImplicitVarNames ? "_rythm" : "rythm", engine);
            if (!underscoreImplicitVarNames) {
                params.put("_rythm", engine); // underscore rythm anyway
            }
            params.put(underscoreImplicitVarNames ? "_request" : "request", request);
            params.put(underscoreImplicitVarNames ? "_response" : "response", response);
            params.put(underscoreImplicitVarNames ? "_session" : "session", request.getSession());
            renderArgs.set(params);
            t.__setRenderArgs(params);
            // TODO fix this: doesn't work when extends is taking place
            // t.render(response.getOutputStream());
            String s = t.render();
            IO.writeContent(s, response.getWriter());
        } catch (RythmException e) {
            engine.render(response.getOutputStream(), "errors/500.html", e);
        } finally {
            renderArgs.remove();
        }
    }
}
