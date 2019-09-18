package uk.co.binarysoup.resthandle;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.freeutils.httpserver.HTTPServer;
import net.freeutils.httpserver.HTTPServer.Request;
import net.freeutils.httpserver.HTTPServer.Response;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


public class RestContextHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Map<Class, Object> resources;

    public RestContextHandler(final Object... restResources) {
        this.resources = Arrays.stream(restResources)
                .collect(Collectors.toMap(Object::getClass, Function.identity(), (old, newVal) -> newVal));
    }

    @HTTPServer.Context(value = "/", methods = "GET")
    public int serve(Request req, Response res) throws IOException, InvocationTargetException, IllegalAccessException {
        final List<Method> pathAnnotatedMethods = resources.keySet().stream()
                .map(Class::getDeclaredMethods)
                .flatMap(Arrays::stream)
                .filter(it -> pathAnnotationMatches(it, req))
                .filter(it -> it.getAnnotation(GET.class) != null)
                .collect(Collectors.toList());

        final Map<String, String> params = req.getParams();
        List<Method> callableMethods;

        if (params.size() == 0) {
            callableMethods = pathAnnotatedMethods.stream()
                    .filter(it -> it.getParameterCount() == 0)
                    .collect(Collectors.toList());
        } else {
            callableMethods = pathAnnotatedMethods.stream()
                    .filter(it -> it.getParameterCount() <= params.size())
                    .filter(it -> anyQueryParametersMatch(it, req))
                    .collect(Collectors.toList());
        }

        validateCallableMethods(res, callableMethods);

        final Method method = callableMethods.get(0);
        Object response = method.invoke(resources.get(method.getDeclaringClass()), queryParametersForCall(req.getParams(), method));
        res.send(200, String.valueOf(response));
        return 0;
    }

    private void validateCallableMethods(final Response res, final List<Method> callableMethods) throws IOException {
        if (callableMethods.size() != 1) {
            res.send(404, "ambiguous rest request, " + callableMethods + " found.");
            throw new IllegalArgumentException("ambiguous rest request");
        }
    }

    private Object[] queryParametersForCall(final Map<String, String> queryParameters,
                                            final Method method) throws IOException {
        final Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        final List<Object> invocationParameters = new ArrayList<>(parameterAnnotations.length);

        for (int index = 0; index < parameterAnnotations.length; index++) {
            final Annotation[] parameterAnnotation = parameterAnnotations[index];
            invocationParameters.addAll(mapQueryParameters(queryParameters, method, index, parameterAnnotation));
        }
        return invocationParameters.toArray(new Object[parameterAnnotations.length]);
    }

    private List<Object> mapQueryParameters(final Map<String, String> params, final Method method, final int i, final Annotation[] parameterAnnotation) throws IOException {
        final ArrayList<Object> invocationParameters = new ArrayList<>();

        for (Annotation annotation : parameterAnnotation) {
            if (Objects.equals(annotation.annotationType(), QueryParam.class)) {
                final QueryParam queryParam = (QueryParam) annotation;
                final Parameter methodParam = method.getParameters()[i];
                invocationParameters.add(OBJECT_MAPPER.readerFor(methodParam.getType()).readValue(params.get(queryParam.value())));
            }
        }
        return invocationParameters;
    }

    private boolean anyQueryParametersMatch(final Method method, final Request request) {
        try {
            return request.getParams().keySet().stream().anyMatch(it ->
                    queryParamAnnotationOrNull(method).contains(it)
            );
        } catch (IOException ignored) {
            return false;
        }
    }

    private Set<String> queryParamAnnotationOrNull(final Method method) {
        final Annotation[][] allParameterAnnotations = method.getParameterAnnotations();
        return Arrays.stream(allParameterAnnotations)
                .flatMap(Arrays::stream)
                .filter(annotation -> annotation.annotationType().equals(QueryParam.class))
                .map(annotation -> (QueryParam) annotation)
                .map(QueryParam::value)
                .collect(Collectors.toSet());
    }

    private boolean pathAnnotationMatches(final Method method, final Request req) {
        final Path annotation = method.getAnnotation(Path.class);
        final String path = req.getPath();
        return annotation != null &&
                annotation.value().equalsIgnoreCase(path);
    }

}
