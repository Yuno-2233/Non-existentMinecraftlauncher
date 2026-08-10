package com.github.yuno2233.neml.mod;

import java.util.*;
import java.util.logging.Logger;
import com.github.yuno2233.neml.log.NemlLogger;

public class DependencyResolver {
    private static final Logger log = NemlLogger.getEngineLogger();

    public static List<ModCandidate> resolve(List<ModCandidate> allMods, String targetModId) throws Exception {
        Map<String, ModCandidate> candidateMap = new HashMap<>();
        for (ModCandidate mod : allMods) candidateMap.put(mod.getId(), mod);

        if (!candidateMap.containsKey(targetModId))
            throw new Exception("未找到 mod: " + targetModId);

        Set<String> resolvedIds = new LinkedHashSet<>();
        collectDependencies(targetModId, candidateMap, resolvedIds, new HashSet<>());
        return topologicalSort(resolvedIds, candidateMap);
    }

    private static void collectDependencies(String modId, Map<String, ModCandidate> map,
                                            Set<String> resolved, Set<String> visiting) throws Exception {
        if (resolved.contains(modId)) return;
        if (visiting.contains(modId)) throw new Exception("循环依赖检测到: " + modId);
        visiting.add(modId);
        ModCandidate mod = map.get(modId);
        if (mod == null) throw new Exception("缺失依赖: " + modId);
        for (String depId : mod.getMetadata().getDepends().keySet()) {
            String range = mod.getMetadata().getDepends().get(depId);
            ModCandidate dep = map.get(depId);
            if (dep == null) throw new Exception("缺失依赖: " + depId + " (被 " + modId + " 需要)");
            if (!versionMatches(dep.getMetadata().getVersion(), range))
                throw new Exception("版本不满足: " + depId + " 需要 " + range + " 但为 " + dep.getMetadata().getVersion());
            collectDependencies(depId, map, resolved, visiting);
        }
        visiting.remove(modId);
        resolved.add(modId);
    }

    static boolean versionMatches(String version, String range) {
        if (version == null || range == null) return false;
        for (String part : range.split("\\s+")) {
            if (part.startsWith(">=")) { if (compareVersion(version, part.substring(2)) < 0) return false; }
            else if (part.startsWith("<=")) { if (compareVersion(version, part.substring(2)) > 0) return false; }
            else if (part.startsWith(">")) { if (compareVersion(version, part.substring(1)) <= 0) return false; }
            else if (part.startsWith("<")) { if (compareVersion(version, part.substring(1)) >= 0) return false; }
            else if (!version.equals(part)) return false;
        }
        return true;
    }

    static int compareVersion(String v1, String v2) {
        String[] p1 = v1.split("\\.");
        String[] p2 = v2.split("\\.");
        int len = Math.max(p1.length, p2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < p1.length ? Integer.parseInt(p1[i]) : 0;
            int n2 = i < p2.length ? Integer.parseInt(p2[i]) : 0;
            if (n1 != n2) return n1 - n2;
        }
        return 0;
    }

    private static List<ModCandidate> topologicalSort(Set<String> ids, Map<String, ModCandidate> map) throws Exception {
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        for (String id : ids) inDegree.put(id, 0);
        for (String id : ids) {
            for (String dep : map.get(id).getMetadata().getDepends().keySet()) {
                if (ids.contains(dep)) inDegree.put(id, inDegree.get(id) + 1);
            }
        }
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> e : inDegree.entrySet())
            if (e.getValue() == 0) queue.add(e.getKey());

        List<ModCandidate> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            sorted.add(map.get(id));
            for (String other : ids) {
                if (map.get(other).getMetadata().getDepends().containsKey(id)) {
                    int newDegree = inDegree.get(other) - 1;
                    inDegree.put(other, newDegree);
                    if (newDegree == 0) queue.add(other);
                }
            }
        }
        if (sorted.size() != ids.size()) throw new Exception("循环依赖，拓扑排序失败");
        return sorted;
    }
}
