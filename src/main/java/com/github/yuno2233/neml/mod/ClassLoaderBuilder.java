package com.github.yuno2233.neml.mod;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class ClassLoaderBuilder {

    public static Map<String, ClassLoader> build(List<ModCandidate> mods) throws Exception {
        Map<String, ClassLoader> loaders = new LinkedHashMap<>();
        for (ModCandidate mod : mods) {
            ClassLoader loader;
            if (mod.getJarPath() == null) {
                // 内置 mod，类由主 classpath 加载
                loader = ClassLoader.getSystemClassLoader();
            } else {
                URL[] urls = { mod.getJarPath().toUri().toURL() };
                loader = new ModClassLoader(urls, null);
            }
            loaders.put(mod.getId(), loader);
        }

        // 设置父子关系（仅外部 mod 需要调整 parent）
        for (ModCandidate mod : mods) {
            ClassLoader current = loaders.get(mod.getId());
            if (current instanceof ModClassLoader) {
                ModClassLoader mcl = (ModClassLoader) current;
                List<ClassLoader> parents = new ArrayList<>();
                for (String depId : mod.getMetadata().getDepends().keySet()) {
                    ClassLoader depLoader = loaders.get(depId);
                    if (depLoader != null) parents.add(depLoader);
                }
                if (parents.isEmpty()) {
                    mcl.setParent(ClassLoader.getSystemClassLoader());
                } else if (parents.size() == 1) {
                    mcl.setParent(parents.get(0));
                } else {
                    mcl.setParent(new CombinedClassLoader(parents));
                }
            }
        }
        return loaders;
    }

    private static class ModClassLoader extends URLClassLoader {
        ModClassLoader(URL[] urls, ClassLoader parent) { super(urls, parent); }
        void setParent(ClassLoader parent) {
            try {
                java.lang.reflect.Field field = ClassLoader.class.getDeclaredField("parent");
                field.setAccessible(true);
                field.set(this, parent);
            } catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    private static class CombinedClassLoader extends ClassLoader {
        List<ClassLoader> parents;
        CombinedClassLoader(List<ClassLoader> parents) { super(null); this.parents = parents; }
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            for (ClassLoader cl : parents) {
                try { return cl.loadClass(name); } catch (ClassNotFoundException ignored) {}
            }
            throw new ClassNotFoundException(name);
        }
    }
}