/*
 * Copyright Amazon.com, Inc. or its affiliates.  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License").
 *  You may not use this file except in compliance with the License.
 *  A copy of the License is located at:
 *
 *       http://aws.amazon.com/apache2.0/
 *
 *  or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package org.partiql.spi.connector

/**
 * A handle refers to a lightweight reference to a specific resource.
 *
 * In the context of the SPI package, handles are used to represent entity in an external data source.
 */
public sealed class ConnectorHandle<T : ConnectorObject> {

    /**
     * The case-normal-form path to an entity in a catalog.
     */
    public abstract val path: ConnectorPath

    /**
     * The catalog entity's metadata.
     */
    public abstract val entity: T

    // TODO: Decide a better naming
    public data class Data(
        override val path: ConnectorPath,
        override val entity: ConnectorObject.Data,
    ) : ConnectorHandle<ConnectorObject.Data>()

    public data class Fn(
        override val path: ConnectorPath,
        override val entity: ConnectorObject.Fn,
    ) : ConnectorHandle<ConnectorObject.Fn>()

    public data class Agg(
        override val path: ConnectorPath,
        override val entity: ConnectorObject.Agg,
    ) : ConnectorHandle<ConnectorObject.Agg>()
}
