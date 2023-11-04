package vm.queryResults;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author Vlada
 */
public abstract class QueryExecutionStatsStoreInterface {

    public abstract void storeStatsForQuery(Object queryObjId, Integer distanceComputationsCount, long time, Object... additionalParametersToStore);

    public void storeStatsForQueries(Map<Object, AtomicInteger> distComps, Map<Object, AtomicLong> times, Map<Object, AtomicLong>... additionalParametersToStore) {
        Set<Object> ids = new HashSet<>();
        ids.addAll(distComps.keySet());
        ids.addAll(times.keySet());
        for (Object id : ids) {
            Integer distComp = distComps.containsKey(id) ? distComps.get(id).get() : null;
            long time = times.containsKey(id) ? times.get(id).get() : -1;
            Object[] stats = new Object[additionalParametersToStore.length];
            if (additionalParametersToStore.length != 0) {
                for (int i = 0; i < additionalParametersToStore.length; i++) {
                    Map<Object, AtomicLong> map = additionalParametersToStore[i];
                    AtomicLong get = map.get(id);
                    stats[i] = get.get();
                }
            }
            storeStatsForQuery(id, distComp, time, stats);
        }
    }

    public abstract void save();

}
