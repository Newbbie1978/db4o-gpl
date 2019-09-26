/* Copyright (C) 2004 - 2006  Versant Inc.  http://www.db4o.com */
package com.db4o.nativequery.optimization;

import java.util.ArrayList;

import EDU.purdue.cs.bloat.context.CachingBloatContext;
import EDU.purdue.cs.bloat.editor.ClassEditor;
import EDU.purdue.cs.bloat.editor.EditorContext;
import EDU.purdue.cs.bloat.file.ClassFileLoader;

import com.db4o.instrumentation.bloat.*;
import com.db4o.instrumentation.core.*;
import com.db4o.internal.query.Db4oNQOptimizer;
import com.db4o.nativequery.expr.Expression;
import com.db4o.query.*;
import com.db4o.reflect.Reflector;
import java.lang.reflect.Method;

// only introduced to keep Db4oListFacade clean of Bloat references
public class Db4oOnTheFlyEnhancer implements Db4oNQOptimizer {

    private transient BloatLoaderContext bloatUtil;
    private transient EditorContext context;
    private Reflector reflector;

    public Db4oOnTheFlyEnhancer() {
        this(new ClassFileLoader());
    }

    public Db4oOnTheFlyEnhancer(Reflector reflector) {
        this(new ClassFileLoader(new Db4oClassSource(new JdkReverseLookupClassFactory(reflector))));
        this.reflector = reflector;
    }

    private Db4oOnTheFlyEnhancer(ClassFileLoader loader) {
        this.context = new CachingBloatContext(loader, new ArrayList(), false);
        this.bloatUtil = new BloatLoaderContext(context);
    }

    @Override
    public Object optimize(Query query, Predicate filter) {
        try {
            //long start=System.currentTimeMillis();
            Expression expr = analyzeInternal(filter);
            //System.err.println((System.currentTimeMillis()-start)+" ms");
            //System.err.println(expr);
            if (expr == null) {
                throw new RuntimeException("Could not analyze " + filter);
            }
            //start=System.currentTimeMillis();
            final JdkReverseLookupClassFactory classFactory = new JdkReverseLookupClassFactory(reflector);
            new SODAQueryBuilder().optimizeQuery(expr, query, filter, classFactory, new BloatReferenceResolver(classFactory));
            //System.err.println((System.currentTimeMillis()-start)+" ms");
            return expr;
        } catch (ClassNotFoundException exc) {
            throw new RuntimeException(exc.getMessage());
        }
    }

    public static class SerializedLambda {

        private Class FClass;
        private Method getImplClass;
        private Method getImplMethodName;
        public Method getInstantiatedMethodType;

        public Method getCapturedArg;
        public Method getCapturedArgCount;

        SerializedLambda() {
            try {
                FClass = Class.forName("java.lang.invoke.SerializedLambda");
                getImplClass = FClass.getMethod("getImplClass");
                getImplMethodName = FClass.getMethod("getImplMethodName");
                getInstantiatedMethodType = FClass.getMethod("getInstantiatedMethodType");

                getCapturedArg = FClass.getMethod("getCapturedArg", Integer.TYPE);
                getCapturedArgCount = FClass.getMethod("getCapturedArgCount");
            } catch (Throwable ex) {

            }
        }

        public Object getMe(Object f) {
            try {
                Method method = f.getClass().getDeclaredMethod("writeReplace");
                method.setAccessible(true);
                return method.invoke(f);
            } catch (Throwable ex) {
                return null;
            }
        }

    }

    public static SerializedLambda serializedLambda = new SerializedLambda();

    private Expression analyzeInternal(Predicate filter) throws ClassNotFoundException {
        if (filter.ExtentInterface == null) {
            ClassEditor classEditor = context.editClass(filter.getClass().getName());
            return new NativeQueryEnhancer().analyze(bloatUtil, classEditor, PredicatePlatform.PREDICATEMETHOD_NAME, null);
        } else {
            Object me = serializedLambda.getMe(filter.ExtentInterface);
            if (me == null) {
                ClassEditor classEditor = context.editClass(filter.ExtentInterface.getClass().getName());
                return new NativeQueryEnhancer().analyze(bloatUtil, classEditor, PredicatePlatform.PREDICATEMETHOD_NAME, null);
            } else {
                String className = null;
                String methodName = null;
                try {
                    className = (String) serializedLambda.getImplClass.invoke(me);
                    methodName = (String) serializedLambda.getImplMethodName.invoke(me);
                } catch (Exception ex) {
                    throw new ClassNotFoundException(ex.getMessage());
                }
                ClassEditor classEditor = context.editClass(className);
                return new NativeQueryEnhancer().analyze(bloatUtil, classEditor, methodName, null);

            }
        }
    }

    public static Expression analyze(Predicate filter) throws ClassNotFoundException {
        return new Db4oOnTheFlyEnhancer().analyzeInternal(filter);
    }
}
