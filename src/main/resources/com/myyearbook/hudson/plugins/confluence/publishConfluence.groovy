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
package com.myyearbook.hudson.plugins.confluence

import org.apache.commons.lang.WordUtils
import org.jenkinsci.Symbol
import com.cloudbees.groovy.cps.NonCPS
import com.myyearbook.hudson.plugins.confluence.wiki.editors.MarkupEditor
import com.myyearbook.hudson.plugins.confluence.wiki.generators.MarkupGenerator

/** Publish to confluence.
 * <code>
 * publishConfluence(siteName:'foo.bar.org', spaceName:'FOO', pageName:'bar') {
 *     append      text:"This goes before"
 *     beforeToken file:"stuff.txt", markerToken:"HERE"
 * }
 * </code>
 */
def call(Map args, Closure body) {
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = new Dsl(args)
    body.call()

    step createPublisher(args)
}

/** Publish to confluence.
 * This overload is added to keep compatibility with snippetgenerator:
 *
 * <code>
 * publishConfluence siteName:'foo.bar.org', spaceName:'FOO', pageName:'bar', editorList: [
 *     confluenceAppendPage(confluenceText plainText:"This goes before"),
 *     beforeToken(confluenceFile(file:"stuff.txt"), markerToken:"HERE")
 * ]
 * </code>
 *
 */
def call(Map args) {
    step createPublisher(args)
}

@NonCPS
private def createPublisher(args) {
    [$class:'com.myyearbook.hudson.plugins.confluence.ConfluencePublisher', *:args]
}

class Dsl implements Serializable {
    private args;

    Dsl(args) {
        this.args = args
        this.args['editorList'] = []
    }

    @NonCPS
    private getSymbolName(descriptor) {
        def symbol = descriptor.class.getAnnotation(Symbol.class)?.value()
        if (!symbol)
            return null

        def symbolMatcher = symbol =~ /^\[confluence(.*)\]$/
        return WordUtils.uncapitalize(symbolMatcher[0][1])
    }

    @NonCPS
    def methodMissing(String name, Object args) {
        // Try an editor matching the name of the method
        def editorClassName = MarkupEditor.all().find { descriptor ->
            def editorName = getSymbolName(descriptor)
            if (name == editorName)
                return true
        }?.clazz?.name
        if (!editorClassName) {
            // Method does not match an editor, so simply set the property of the same name
            if (args.length != 1)
                throw new MissingMethodException(name, delegate, args)
            this.args[name] = args[0]
            return
        }

        Map namedArgs
        if (args.length >= 1 && args[0] instanceof Map) {
            // Editing commands called with named arguments: replace generator argument
            namedArgs = args[0] as Map
            MarkupGenerator.all().any { descriptor ->
                def propertyName = getSymbolName(descriptor)
                if (!propertyName || !namedArgs.containsKey(propertyName))
                    return

                def value = namedArgs[propertyName]
                namedArgs.remove(propertyName)
                namedArgs['generator'] = [$class: descriptor.clazz.name, "$propertyName": value]
                return true
            }
        } else if (args.length == 1 && args[0] instanceof String) {
            // Editing commands called with unnamed string argument: use plain text generator
            namedArgs = [:]
            namedArgs['generator'] = [$class: 'com.myyearbook.hudson.plugins.confluence.wiki.generators.PlainTextGenerator',
                                      text: args[0]]
        } else {
            throw new MissingMethodException(name, delegate, args)
        }

        this.args['editorList'] << [$class:editorClassName, *:namedArgs]
    }

    @NonCPS
    def propertyMissing(String name) {
        args.getAt(name)
    }
}
