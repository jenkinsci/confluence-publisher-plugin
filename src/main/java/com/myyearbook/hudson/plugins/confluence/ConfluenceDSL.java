/*
 * Copyright 2017 Francois Ferrand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.myyearbook.hudson.plugins.confluence;

import groovy.lang.Binding;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;

@Extension
public class ConfluenceDSL extends GlobalVariable {
    @Override
    public String getName() {
        return "publishConfluence";
    }

    @Override
    public Object getValue(CpsScript script) throws Exception {
        Binding binding = script.getBinding();

        Object confluenceDsl;
        if (binding.hasVariable(getName())) {
            confluenceDsl = binding.getVariable(getName());
        } else {
            confluenceDsl = script.getClass().getClassLoader()
                    .loadClass("com.myyearbook.hudson.plugins.confluence." + getName())
                    .newInstance();
            binding.setVariable(getName(), confluenceDsl);
        }
        return confluenceDsl;
    }
}
