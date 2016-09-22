/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wrmsr.wava.java.javac.option;

import com.google.common.collect.ImmutableList;

import javax.annotation.concurrent.Immutable;

import java.io.File;
import java.util.List;

import static java.util.Objects.requireNonNull;

@Immutable
public abstract class DirectoryOption
        implements JavacOption
{
    private final File directory;

    public DirectoryOption(File directory)
    {
        this.directory = requireNonNull(directory);
    }

    public File getDirectory()
    {
        return directory;
    }

    protected abstract String getPrefix();

    @Override
    public List<String> getArgs()
    {
        return ImmutableList.of(getPrefix(), directory.getPath());
    }
}
