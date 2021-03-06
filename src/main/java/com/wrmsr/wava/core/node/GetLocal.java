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
package com.wrmsr.wava.core.node;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.collect.ImmutableList;
import com.wrmsr.wava.core.node.visitor.Visitor;
import com.wrmsr.wava.core.type.Index;
import com.wrmsr.wava.core.type.Type;

import javax.annotation.concurrent.Immutable;

import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

@JsonPropertyOrder({
        "index",
        "type",
})
@Immutable
public final class GetLocal
        extends Node
{
    private final Index index;
    private final Type type;

    public GetLocal(
            @JsonProperty("index") Index index,
            @JsonProperty("type") Type type)
    {
        this.index = requireNonNull(index);
        this.type = requireNonNull(type);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GetLocal getLocal = (GetLocal) o;
        return Objects.equals(index, getLocal.index) &&
                type == getLocal.type;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(index, type);
    }

    @JsonProperty("index")
    public Index getIndex()
    {
        return index;
    }

    @JsonProperty("type")
    public Type getType()
    {
        return type;
    }

    @Override
    public List<Node> getChildren()
    {
        return ImmutableList.of();
    }

    @Override
    public <C, R> R accept(Visitor<C, R> visitor, C context)
    {
        return visitor.visitGetLocal(this, context);
    }
}
