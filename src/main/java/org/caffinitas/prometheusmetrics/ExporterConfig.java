/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.caffinitas.prometheusmetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ExporterConfig {
    public boolean ssl;
    public String bindAddress = "127.0.0.1";
    public int httpPort = 8088;
    public boolean includeJvm;

    public List<Mapping> mappings = new ArrayList<>();
    public List<Exclusion> exclusions = new ArrayList<>();

    public final static class Mapping {
        Pattern regex;
        public String pattern;
        public String name;
        public List<Label> labels = new ArrayList<>();
    }

    public final static class Exclusion {
        Pattern regex;
        public String pattern;
    }

    public final static class Label {
        public String label;
        public String value;
    }
}
