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
package com.wrmsr.wava.transform;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.wrmsr.wava.analyze.ControlTransferAnalysis;
import com.wrmsr.wava.core.node.Block;
import com.wrmsr.wava.core.node.Break;
import com.wrmsr.wava.core.node.BreakTable;
import com.wrmsr.wava.core.node.Call;
import com.wrmsr.wava.core.node.GetLocal;
import com.wrmsr.wava.core.node.Label;
import com.wrmsr.wava.core.node.Loop;
import com.wrmsr.wava.core.node.Node;
import com.wrmsr.wava.core.node.Nop;
import com.wrmsr.wava.core.node.SetLocal;
import com.wrmsr.wava.core.node.visitor.Visitor;
import com.wrmsr.wava.core.type.Index;
import com.wrmsr.wava.core.type.Name;
import com.wrmsr.wava.util.Cell;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkState;
import static com.wrmsr.wava.core.node.Nodes.nodify;
import static com.wrmsr.wava.core.node.Nodes.reconstructNode;
import static com.wrmsr.wava.core.node.Nodes.rewriteNode;
import static com.wrmsr.wava.util.function.Functions.negate;
import static com.wrmsr.wava.util.collect.MoreCollectors.toImmutableList;
import static java.util.Objects.requireNonNull;

public final class Transforms
{
    private Transforms()
    {
    }

    public static Node eliminateUnreachable(Node root, ControlTransferAnalysis controlFlowAnalyses)
    {
        return root.accept(new Visitor<Void, Node>()
        {
            @Override
            protected Node visitNode(Node node, Void context)
            {
                return reconstructNode(node, node.getChildren().stream().map(c -> c.accept(this, context)).iterator());
            }

            @Override
            public Node visitBlock(Block node, Void context)
            {
                ImmutableList.Builder<Node> builder = ImmutableList.builder();
                Iterator<Node> iterator = node.getChildren().iterator();
                while (iterator.hasNext()) {
                    Node child = iterator.next();
                    ControlTransferAnalysis.Entry cfa = requireNonNull(controlFlowAnalyses.get(child));
                    if (cfa.getExecution() == ControlTransferAnalysis.Execution.UNREACHABLE) {
                        break;
                    }
                    builder.add(visitNode(child, context));
                }
                while (iterator.hasNext()) {
                    Node child = iterator.next();
                    ControlTransferAnalysis.Entry cfa = requireNonNull(controlFlowAnalyses.get(child));
                    checkState(cfa.getExecution() == ControlTransferAnalysis.Execution.UNREACHABLE);
                }
                return new Block(
                        builder.build());
            }
        }, null);
    }

    public static Node eliminateUnreferencedLabels(Node root, Set<Name> referencedNames)
    {
        return rewriteNode(root, new Visitor<Void, Node>()
        {
            @Override
            protected Node visitNode(Node node, Void context)
            {
                return node;
            }

            @Override
            public Node visitLabel(Label node, Void context)
            {
                if (!referencedNames.contains(node.getName())) {
                    return node.getBody();
                }
                else {
                    return node;
                }
            }

            @Override
            public Node visitLoop(Loop node, Void context)
            {
                if (!referencedNames.contains(node.getName())) {
                    return node.getBody();
                }
                else {
                    return node;
                }
            }
        }, null);
    }

    public static Node remapLocals(Node root, Map<Index, Index> indexMap)
    {
        return rewriteNode(root, new Visitor<Void, Node>()
        {
            @Override
            protected Node visitNode(Node node, Void context)
            {
                return node;
            }

            @Override
            public Node visitGetLocal(GetLocal node, Void context)
            {
                return new GetLocal(
                        indexMap.get(node.getIndex()),
                        node.getType());
            }

            @Override
            public Node visitSetLocal(SetLocal node, Void context)
            {
                return new SetLocal(
                        indexMap.get(node.getIndex()),
                        node.getType(),
                        node.getValue());
            }
        }, null);
    }

    // FIXME: keep tail nops as explicit value discards? or just don't apply this when that's wanted
    public static Node squishBlocks(Node root)
    {
        return rewriteNode(root, new Visitor<Void, Node>()
        {
            @Override
            protected Node visitNode(Node node, Void context)
            {
                return node;
            }

            @Override
            public Node visitBlock(Block node, Void context)
            {
                List<Node> children = node.getChildren().stream()
                        .flatMap(n -> n instanceof Block ? n.getChildren().stream() : Stream.of(n))
                        .filter(negate(Nop.class::isInstance))
                        .collect(toImmutableList());
                return nodify(children);
            }
        }, null);
    }

    public static Node mangleLabelNames(Node root, Function<Name, Name> mangle)
    {
        return rewriteNode(root, new Visitor<Void, Node>()
        {
            @Override
            protected Node visitNode(Node node, Void context)
            {
                return node;
            }

            @Override
            public Node visitBreak(Break node, Void context)
            {
                return new Break(
                        mangle.apply(node.getTarget()),
                        node.getValue());
            }

            @Override
            public Node visitBreakTable(BreakTable node, Void context)
            {
                return new BreakTable(
                        node.getTargets().stream().map(mangle).collect(toImmutableList()),
                        mangle.apply(node.getDefaultTarget()),
                        node.getCondition());
            }

            @Override
            public Node visitLabel(Label node, Void context)
            {
                return new Label(
                        mangle.apply(node.getName()),
                        node.getBody());
            }

            @Override
            public Node visitLoop(Loop node, Void context)
            {
                return new Loop(
                        mangle.apply(node.getName()),
                        node.getBody());
            }
        }, null);
    }

    public static Node mangleCallNames(Node root, Function<Name, Name> mangle)
    {
        return rewriteNode(root, new Visitor<Void, Node>()
        {
            @Override
            protected Node visitNode(Node node, Void context)
            {
                return node;
            }

            @Override
            public Node visitCall(Call node, Void context)
            {
                Call.Target target = node.getTarget();
                if (target instanceof Call.DirectTarget) {
                    target = new Call.DirectTarget(
                            mangle.apply(((Call.DirectTarget) target).getName()));
                }
                else if (target instanceof Call.HostTarget) {
                    // nop
                }
                else if (target instanceof Call.ImportTarget) {
                    target = new Call.ImportTarget(
                            mangle.apply(((Call.ImportTarget) target).getName()));
                }
                else {
                    throw new IllegalStateException();
                }
                return new Call(
                        target,
                        node.getSignature(),
                        node.getOperands());
            }
        }, null);
    }

    public static Node uniquifyLabels(Node root, Supplier<Name> nameSupplier)
    {
        Map<Name, Name> remappedNames = new HashMap<>();
        return root.accept(new Visitor<Void, Node>()
        {
            @Override
            protected Node visitNode(Node node, Void context)
            {
                return reconstructNode(node, node.getChildren().stream().map(c -> c.accept(this, context)).iterator());
            }

            private Node processNamedNode(Node node, Name name, BiFunction<Name, Node, Node> ctor, Void context)
            {
                Name prevName = remappedNames.get(name);
                Name newName = prevName == null ? name : nameSupplier.get();
                remappedNames.put(name, newName);
                Node child = Iterables.getOnlyElement(node.getChildren());
                Node ret = ctor.apply(newName, child.accept(this, context));
                if (prevName == null) {
                    remappedNames.remove(name);
                }
                else {
                    remappedNames.put(name, prevName);
                }
                return ret;
            }

            @Override
            public Node visitBreak(Break node, Void context)
            {
                return new Break(
                        requireNonNull(remappedNames.get(node.getTarget())),
                        node.getValue().accept(this, context));
            }

            @Override
            public Node visitBreakTable(BreakTable node, Void context)
            {
                return new BreakTable(
                        node.getTargets().stream().map(remappedNames::get).map(Objects::requireNonNull).collect(toImmutableList()),
                        requireNonNull(remappedNames.get(node.getDefaultTarget())),
                        node.getCondition().accept(this, context));
            }

            @Override
            public Node visitLabel(Label node, Void context)
            {
                return processNamedNode(node, node.getName(), Label::new, context);
            }

            @Override
            public Node visitLoop(Loop node, Void context)
            {
                return processNamedNode(node, node.getName(), Loop::new, context);
            }
        }, null);
    }

    public static Node replaceNode(Node root, Node what, Node with, boolean isRequired)
    {
        Cell<Boolean> hasReplaced = Cell.of(false);
        Node ret = root.accept(new Visitor<Void, Node>()
        {
            @Override
            protected Node visitNode(Node node, Void context)
            {
                if (node == what) {
                    checkState(!hasReplaced.get());
                    hasReplaced.set(true);
                    return with;
                }
                else {
                    return reconstructNode(node, node.getChildren().stream().map(c -> c.accept(this, context)).iterator());
                }
            }
        }, null);
        if (isRequired && !hasReplaced.get()) {
            throw new IllegalStateException();
        }
        return ret;
    }
}
