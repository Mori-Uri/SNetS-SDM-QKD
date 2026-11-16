package simulator;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Parte 66: wraps the algorithms of the traced simulation so that each call to them (routing, modulation selection,
 * core and spectrum assignment, integrated RMCSA...) is reported to the trace with what it left in the candidate.
 * The proxy only forwards the call (same arguments, result, exceptions and order). The handler is serializable, so
 * the save states keep working. PABS power assignment is not wrapped (the blocking metric reads it through
 * instanceof), as in AlgorithmCostRecorder.
 */
public final class TraceAlgorithms {

    private TraceAlgorithms() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T wrap(T target, Class<T> iface, String component) {
        if (target == null || !iface.isInterface() || target.getClass().getName().contains("PABSAlgorithm")) {
            return target;
        }
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface}, new Handler(target, component));
    }

    private static final class Handler implements InvocationHandler, Serializable {
        private static final long serialVersionUID = 1L;
        private final Object target;
        private final String component;

        Handler(Object target, String component) {
            this.target = target;
            this.component = component;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                if ("equals".equals(method.getName())) return Boolean.valueOf(proxy == args[0]);
                if ("hashCode".equals(method.getName())) return Integer.valueOf(System.identityHashCode(proxy));
                if ("toString".equals(method.getName())) return target.toString();
            }
            final Object result;
            try {
                result = method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            TraceProbe.call(component, method.getName(), result, args);
            return result;
        }
    }
}
