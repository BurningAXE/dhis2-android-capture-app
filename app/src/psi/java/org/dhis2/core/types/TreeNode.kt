package org.dhis2.core.types

sealed class TreeNode<T>(val content: T) {
    protected var parent: TreeNode<*>? = null

    val level: Int
        get() {
            return if (parent == null) {
                0
            } else {
                val parentLevel = parent!!.level
                parentLevel + 1
            }
        }

    class Node<N>(
        private val nodeContent: N,
        initialChildren: List<TreeNode<*>> = mutableListOf(),
        var expanded: Boolean = false
    ) : TreeNode<N>(nodeContent) {
        private val internalChildren: MutableList<TreeNode<*>> = initialChildren.toMutableList()

        init {
            children.forEach {
                it.parent = this
            }
        }

        val children: List<TreeNode<*>>
            get() {
                return internalChildren.toList()
            }

        fun node(content: N, initialize: (Node<N>.() -> Unit)? = null) {
            val child = Node(content)

            addChild(child)
            if (initialize != null) {
                child.initialize()
            }
        }

        fun <L> leaf(content: L) {
            addChild(Leaf(content))
        }

        fun addChild(node: TreeNode<*>, index: Int = internalChildren.size) {
            internalChildren.add(index, node)
            node.parent = this
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Node<*>

            if (expanded != other.expanded) return false
            if (internalChildren != other.internalChildren) return false
            if (content != other.content) return false

            return true
        }

        override fun hashCode(): Int {
            var result = expanded.hashCode()
            result = 31 * result + internalChildren.hashCode()
            return result
        }
    }

    data class Leaf<L>(private val leafContent: L) : TreeNode<L>(leafContent)
}

fun <N> root(content: N, initialize: (TreeNode.Node<N>.() -> Unit)? = null): TreeNode.Node<N> {
    val node = TreeNode.Node(content)
    if (initialize != null) {
        node.initialize()
    }
    return node
}
