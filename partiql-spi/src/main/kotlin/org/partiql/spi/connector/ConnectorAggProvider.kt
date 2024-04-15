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

import org.partiql.spi.fn.Agg
import org.partiql.spi.fn.FnExperimental

/**
 * A [ConnectorAggProvider] implementation is responsible for providing an aggregation function implementation given a handle.
 */
@FnExperimental
public interface ConnectorAggProvider {
    public fun getAgg(path: ConnectorPath, specific: String): Agg?
}