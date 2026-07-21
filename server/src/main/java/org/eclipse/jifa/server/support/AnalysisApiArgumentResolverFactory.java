/********************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.jifa.server.support;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.eclipse.jifa.analysis.Api;
import org.eclipse.jifa.analysis.ApiParameter;
import org.eclipse.jifa.server.domain.entity.shared.file.FileEntity;

import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.eclipse.jifa.common.util.GsonHolder.GSON;

public class AnalysisApiArgumentResolverFactory {

    private static final Map<AnalysisApiArgumentResolver, AnalysisApiArgumentResolver> RESOLVERS = new HashMap<>();

    private static final Map<ApiParameter, LeafResolver<?>> LEAF_RESOLVERS = new HashMap<>();

    public static AnalysisApiArgumentResolver build(Api api) {
        ApiParameter[] parameters = api.parameters();
        if (parameters.length == 0) {
            return AnalysisApiArgumentResolver.NO_ARGS;
        }
        LeafResolver<?>[] resolvers = new LeafResolver[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            resolvers[i] = buildLeafResolver(parameters[i]);
        }
        AnalysisApiArgumentResolver resolver = resolvers.length == 1
                ? new SingleArgumentResolver(resolvers[0])
                : new CompositeArgumentResolver(resolvers);
        return RESOLVERS.computeIfAbsent(resolver, r -> r);
    }

    static class CompositeArgumentResolver implements AnalysisApiArgumentResolver {
        private final LeafResolver<?>[] resolvers;

        CompositeArgumentResolver(LeafResolver<?>[] resolvers) {
            this.resolvers = resolvers;
        }

        @Override
        public Object[] resolve(AnalysisApiArgumentContext context) {
            Object[] result = new Object[resolvers.length];
            for (int i = 0; i < resolvers.length; i++) {
                result[i] = resolvers[i].resolve(context);
            }
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CompositeArgumentResolver that = (CompositeArgumentResolver) o;
            return Arrays.equals(resolvers, that.resolvers);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(resolvers);
        }
    }

    static class SingleArgumentResolver implements AnalysisApiArgumentResolver {

        private final LeafResolver<?> resolver;

        SingleArgumentResolver(LeafResolver<?> resolver) {
            this.resolver = resolver;
        }

        @Override
        public Object[] resolve(AnalysisApiArgumentContext context) {
            return new Object[]{resolver.resolve(context)};
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SingleArgumentResolver that = (SingleArgumentResolver) o;
            return resolver.equals(that.resolver);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resolver);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static LeafResolver buildLeafResolver(ApiParameter parameter) {
        Type type = parameter.type();
        String name = parameter.name();
        boolean required = parameter.required();
        boolean targetPath = parameter.targetPath();
        boolean comparisonTargetPath = parameter.comparisonTargetPath();

        return LEAF_RESOLVERS.computeIfAbsent
                                     (parameter,
                                      k -> {
                                          if (type == boolean.class || type == Boolean.class) {
                                              return new BooleanResolver(name, required);
                                          } else if (type == int.class || type == Integer.class) {
                                              return new IntResolver(name, required);
                                          } else if (type == long.class || type == Long.class) {
                                              return new LongResolver(name, required);
                                          } else if (type == double.class || type == DoubleResolver.class) {
                                              return new DoubleResolver(name, required);
                                          } else if (type == String.class) {
                                              return new StringResolver(name, required);
                                          } else if (type == Path.class) {
                                              if (targetPath) {
                                                  return new TargetPathResolver(name);
                                              } else {
                                                  assert comparisonTargetPath;
                                                  return new ComparisonPathResolver(name, required);
                                              }
                                          } else {
                                              Class<?> raw;
                                              if (type instanceof Class<?> clazz) {
                                                  raw = clazz;
                                              } else if (type instanceof ParameterizedType pt) {
                                                  raw = (Class<?>) pt.getRawType();
                                              } else {
                                                  throw new IllegalArgumentException("Unsupported type: " + type);
                                              }
                                              if (raw.isEnum()) {
                                                  return new EnumResolver(name, required, raw);
                                              } else if (!raw.isPrimitive()) {
                                                  return new ObjectResolver(name, required, type);
                                              } else {
                                                  throw new IllegalArgumentException("Unsupported type: " + type);
                                              }
                                          }
                                      }
                                     );
    }

    static abstract class LeafResolver<T> {

        protected final String key;

        protected final boolean required;

        LeafResolver(String key, boolean required) {
            this.key = key;
            this.required = required;
        }

        abstract T resolve(AnalysisApiArgumentContext context);
    }

    static class BooleanResolver extends LeafResolver<Boolean> {

        public BooleanResolver(String name, boolean required) {
            super(name, required);
        }

        @Override
        Boolean resolve(AnalysisApiArgumentContext context) {
            JsonElement element = context.paramJson().get(key);
            if (element != null) {
                if (!element.isJsonPrimitive()) {
                    throw new IllegalArgumentException(key + " must be a boolean");
                }
                try {
                    return element.getAsJsonPrimitive().getAsBoolean();
                } catch (Throwable t) {
                    throw new IllegalArgumentException(key + " must be a boolean");
                }
            } else if (!required) {
                return false;
            }
            throw new IllegalArgumentException(key + " is required");
        }
    }

    static class IntResolver extends LeafResolver<Integer> {

        public IntResolver(String name, boolean required) {
            super(name, required);
        }

        @Override
        Integer resolve(AnalysisApiArgumentContext context) {
            JsonElement element = context.paramJson().get(key);
            if (element != null) {
                if (!element.isJsonPrimitive()) {
                    throw new IllegalArgumentException(key + " must be a int");
                }
                try {
                    return element.getAsJsonPrimitive().getAsInt();
                } catch (Throwable t) {
                    throw new IllegalArgumentException(key + " must be a int");
                }
            } else if (!required) {
                return 0;
            }
            throw new IllegalArgumentException(key + " is required");
        }
    }

    static class LongResolver extends LeafResolver<Long> {

        public LongResolver(String name, boolean required) {
            super(name, required);
        }

        @Override
        Long resolve(AnalysisApiArgumentContext context) {
            JsonElement element = context.paramJson().get(key);
            if (element != null) {
                if (!element.isJsonPrimitive()) {
                    throw new IllegalArgumentException(key + " must be a long");
                }
                try {
                    return element.getAsJsonPrimitive().getAsLong();
                } catch (Throwable t) {
                    throw new IllegalArgumentException(key + " must be a long");
                }
            } else if (!required) {
                return 0L;
            }
            throw new IllegalArgumentException(key + " is required");
        }
    }

    static class DoubleResolver extends LeafResolver<Double> {

        public DoubleResolver(String name, boolean required) {
            super(name, required);
        }

        @Override
        Double resolve(AnalysisApiArgumentContext context) {
            JsonElement element = context.paramJson().get(key);
            if (element != null) {
                if (!element.isJsonPrimitive()) {
                    throw new IllegalArgumentException(key + " must be a double");
                }
                try {
                    return element.getAsJsonPrimitive().getAsDouble();
                } catch (Throwable t) {
                    throw new IllegalArgumentException(key + " must be a double");
                }
            } else if (!required) {
                return 0.0;
            }
            throw new IllegalArgumentException(key + " is required");
        }
    }

    static class StringResolver extends LeafResolver<String> {

        public StringResolver(String name, boolean required) {
            super(name, required);
        }

        @Override
        String resolve(AnalysisApiArgumentContext context) {
            JsonElement element = context.paramJson().get(key);
            if (element != null && !(element instanceof JsonNull)) {
                if (!element.isJsonPrimitive()) {
                    throw new IllegalArgumentException(key + " must be a string");
                }
                try {
                    return element.getAsJsonPrimitive().getAsString();
                } catch (Throwable t) {
                    throw new IllegalArgumentException(key + " must be a string");
                }
            } else if (!required) {
                return null;
            }
            throw new IllegalArgumentException(key + " is required");
        }
    }

    static class EnumResolver<E extends Enum<E>> extends LeafResolver<E> {

        private final Class<E> enumClass;

        EnumResolver(String name, boolean required, Class<E> enumClass) {
            super(name, required);
            this.enumClass = enumClass;
        }

        @Override
        E resolve(AnalysisApiArgumentContext context) {
            JsonElement element = context.paramJson().get(key);
            if (element != null && !(element instanceof JsonNull)) {
                if (!element.isJsonPrimitive()) {
                    throw new IllegalArgumentException(key + " must be a " + enumClass.getName());
                }
                try {
                    String value = element.getAsJsonPrimitive().getAsString();
                    E[] ecs = enumClass.getEnumConstants();
                    for (E ec : ecs) {
                        if (ec.name().equalsIgnoreCase(value)) {
                            return ec;
                        }
                    }
                } catch (Throwable t) {
                    throw new IllegalArgumentException(key + " must be a " + enumClass.getName());
                }
            } else if (!required) {
                return null;
            }
            throw new IllegalArgumentException(key + " is required");
        }
    }

    static class TargetPathResolver extends LeafResolver<Path> {

        TargetPathResolver(String name) {
            super(name, true);
        }

        @Override
        Path resolve(AnalysisApiArgumentContext context) {
            return context.target();
        }
    }

    static class ComparisonPathResolver extends LeafResolver<Path> {

        ComparisonPathResolver(String name, boolean required) {
            super(name, required);
        }

        @Override
        Path resolve(AnalysisApiArgumentContext context) {
            JsonElement element = context.paramJson().get(key);
            if (element == null || element instanceof JsonNull) {
                if (!required) {
                    return null;
                }
                throw new IllegalArgumentException(key + " is required");
            }
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(key + " must be the name of a comparison target");
            }
            String name = element.getAsString();
            FileEntity file = context.fileService().getFileByUniqueName(name, context.type());
            return context.storageService().locationOf(file.getType(), file.getUniqueName());
        }
    }

    static class ObjectResolver extends LeafResolver<Object> {

        private final Type type;

        ObjectResolver(String name, boolean required, Type type) {
            super(name, required);
            this.type = type;
        }

        @Override
        Object resolve(AnalysisApiArgumentContext context) {

            JsonObject jo = context.paramJson();
            if (!jo.has(key)) {
                try {
                    return GSON.fromJson(jo, type);
                } catch (RuntimeException re) {
                    if (!required) {
                        return null;
                    }
                    if (type instanceof Class<?> clazz) {
                        try {
                            Constructor<?> constructor = clazz.getConstructor();
                            return constructor.newInstance();
                        } catch (Exception ignored) {
                            throw re;
                        }
                    }
                }
            }

            JsonElement element = jo.get(key);
            if (type instanceof Class<?> clazz) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    if (clazz.isArray()) {
                        element = GSON.fromJson(element.getAsString(), JsonArray.class);
                    } else if (clazz != String.class) {
                        element = GSON.fromJson(element.getAsString(), JsonObject.class);
                    }
                }
            }
            return GSON.fromJson(element, type);
        }
    }
}
