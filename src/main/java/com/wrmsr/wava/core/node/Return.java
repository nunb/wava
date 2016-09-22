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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.collect.ImmutableList;
import com.wrmsr.wava.core.node.visitor.Visitor;

import javax.annotation.concurrent.Immutable;

import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

@JsonPropertyOrder({
        "value",
})
@Immutable
public final class Return
        extends Node
{
    private final Node value;

    @JsonCreator
    public Return(
            @JsonProperty("value") Node value)
    {
        this.value = requireNonNull(value);
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
        Return aReturn = (Return) o;
        return Objects.equals(value, aReturn.value);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(value);
    }

    @JsonProperty("value")
    public Node getValue()
    {
        return value;
    }

    @Override
    public List<Node> getChildren()
    {
        return ImmutableList.of(value);
    }

    @Override
    public <C, R> R accept(Visitor<C, R> visitor, C context)
    {
        return visitor.visitReturn(this, context);
    }
}
