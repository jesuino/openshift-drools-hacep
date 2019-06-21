/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.remote;

import java.util.concurrent.CompletableFuture;

import org.kie.api.runtime.ObjectFilter;

public interface RemoteCepEntryPoint<T> {

    /**
     * @return the String Id of this entry point
     */
    String getEntryPointId();

    /**
     * Inserts a new fact into this entry point
     *
     * @param object
     *        the fact to be inserted
     *
     * @return the fact handle created for the given fact
     */
    void insert(Object object);

    /**
     * <p>This class is <i>not</i> a general-purpose <tt>Collection</tt>
     * implementation!  While this class implements the <tt>Collection</tt> interface, it
     * intentionally violates <tt>Collection</tt> general contract, which mandates the
     * use of the <tt>equals</tt> method when comparing objects.</p>
     *
     * <p>Instead the approach used when comparing objects with the <tt>contains(Object)</tt>
     * method is dependent on the WorkingMemory configuration, where it can be configured for <tt>Identity</tt>
     * or for <tt>Equality</tt>.</p>
     *
     * @param  callback to read all facts from the current session as a Collection.
     */
    void getObjects(CompletableFuture<T> callback);

    /**
     * @param filter the filter to be applied to the returned collection of facts.
     * @@param  callback to read all facts from the current session that are accepted by the given <code>ObjectFilter</code>.
     */
    void getObjects(CompletableFuture<T> callback, ObjectFilter filter);

    /**
     * @param callback to read the total number of facts currently in this entry point
     */
    void getFactCount(CompletableFuture<T> callback);
}
