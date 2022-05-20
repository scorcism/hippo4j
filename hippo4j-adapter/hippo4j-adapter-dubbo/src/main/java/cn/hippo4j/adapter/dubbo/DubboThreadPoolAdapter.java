/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.hippo4j.adapter.dubbo;

import cn.hippo4j.adapter.base.ThreadPoolAdapter;
import cn.hippo4j.adapter.base.ThreadPoolAdapterParameter;
import cn.hippo4j.adapter.base.ThreadPoolAdapterState;
import cn.hippo4j.common.toolkit.ReflectUtil;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.store.DataStore;
import org.apache.dubbo.common.threadpool.manager.ExecutorRepository;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dubbo thread-pool adapter.
 */
@Slf4j
public class DubboThreadPoolAdapter implements ThreadPoolAdapter, ApplicationListener<ApplicationStartedEvent> {

    private final Map<String, ThreadPoolExecutor> DUBBO_PROTOCOL_EXECUTOR = Maps.newHashMap();

    @Override
    public String mark() {
        return "Dubbo";
    }

    @Override
    public ThreadPoolAdapterState getThreadPoolState(String identify) {
        ThreadPoolAdapterState threadPoolAdapterState = new ThreadPoolAdapterState();
        final ThreadPoolExecutor executor = DUBBO_PROTOCOL_EXECUTOR.get(identify);
        if (executor == null) {
            return threadPoolAdapterState;
        }
        threadPoolAdapterState.setThreadPoolKey(identify);
        threadPoolAdapterState.setCoreSize(executor.getCorePoolSize());
        threadPoolAdapterState.setMaximumSize(executor.getMaximumPoolSize());
        return threadPoolAdapterState;
    }

    @Override
    public List<ThreadPoolAdapterState> getThreadPoolStates() {
        List<ThreadPoolAdapterState> threadPoolAdapterStates = new ArrayList<>();
        DUBBO_PROTOCOL_EXECUTOR.forEach((kel, val) -> threadPoolAdapterStates.add(getThreadPoolState(String.valueOf(val))));
        return threadPoolAdapterStates;
    }

    @Override
    public boolean updateThreadPool(ThreadPoolAdapterParameter threadPoolAdapterParameter) {
        final ThreadPoolExecutor executor = DUBBO_PROTOCOL_EXECUTOR.get(threadPoolAdapterParameter.getThreadPoolKey());
        if (executor == null) {
            return false;
        }
        executor.setCorePoolSize(threadPoolAdapterParameter.getCorePoolSize());
        executor.setMaximumPoolSize(threadPoolAdapterParameter.getMaximumPoolSize());
        return true;
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        boolean is2xVersion = false;
        String poolKey = ExecutorService.class.getName();
        if (Version.getIntVersion(Version.getVersion()) < 3000000) {
            is2xVersion = true;
        }
        try {
            if (is2xVersion) {
                DataStore dataStore = ExtensionLoader.getExtensionLoader(DataStore.class).getDefaultExtension();
                Map<String, Object> executors = dataStore.get(poolKey);
                executors.forEach((key, value) -> DUBBO_PROTOCOL_EXECUTOR.put(key, (ThreadPoolExecutor) value));
                return;
            }
            ExecutorRepository executorRepository = ExtensionLoader.getExtensionLoader(ExecutorRepository.class).getDefaultExtension();
            ConcurrentMap<String, ConcurrentMap<Integer, ExecutorService>> data =
                    (ConcurrentMap<String, ConcurrentMap<Integer, ExecutorService>>) ReflectUtil.getFieldValue(executorRepository, "data");
            ConcurrentMap<Integer, ExecutorService> executorServiceMap = data.get(poolKey);
            executorServiceMap.forEach((key, value) -> DUBBO_PROTOCOL_EXECUTOR.put(String.valueOf(key), (ThreadPoolExecutor) value));
        } catch (Exception ex) {
            log.error("Failed to get Dubbo {}.X protocol thread pool", is2xVersion ? "2" : "3", ex);
        }
    }
}
