package com.github.yuno2233.neml.mod;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class ClassLoaderBuilder {

    public static Map<String, ClassLoader> build(List<ModCandidate> mods) throws Exception {
        Map<String, ClassLoader> loaders = new LinkedHashMap<>();

        // mods 已经是拓扑排序，依赖的 mod 一定先出现
        for (ModCandidate mod : mods) {
            // 收集直接依赖的 ClassLoader
            List<ClassLoader> parentLoaders = new ArrayList<>();
            for (String depId : mod.getMetadata().getDepends().keySet()) {
                ClassLoader depLoader = loaders.get(depId);
                if (depLoader != null) {
                    parentLoaders.add(depLoader);
                }
            }

            ClassLoader parent;
            if (parentLoaders.isEmpty()) {
                parent = ClassLoader.getSystemClassLoader();
            } else if (parentLoaders.size() == 1) {
                parent = parentLoaders.get(0);
            } else {
                parent = new CombinedClassLoader(parentLoaders);
            }

            ClassLoader loader;
            if (mod.getJarPath() == null) {
                // 内置 mod，使用系统类加载器（其类已在主 jar 中）
                loader = ClassLoader.getSystemClassLoader();
            } else {
                loader = new ModClassLoader(
                        new URL[]{mod.getJarPath().toUri().toURL()},
                        parent
                );
            }
            loaders.put(mod.getId(), loader);
        }
        return loaders;
    }

    private static class ModClassLoader extends URLClassLoader {
        ModClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }
    }

    private static class CombinedClassLoader extends ClassLoader {
        private final List<ClassLoader> parents;

        CombinedClassLoader(List<ClassLoader> parents) {
            super(null);
            this.parents = parents;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            for (ClassLoader cl : parents) {
                try {
                    return cl.loadClass(name);
                } catch (ClassNotFoundException ignored) {}
            }
            throw new ClassNotFoundException(name);
        }
    }
}