package indi.bookmarkx.tree;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import indi.bookmarkx.common.I18N;
import indi.bookmarkx.dialog.BookmarkCreatorDialog;
import indi.bookmarkx.model.BookmarkNodeModel;
import indi.bookmarkx.model.GroupNodeModel;
import org.apache.commons.lang3.Validate;
import org.jsoup.internal.StringUtil;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Nonoas
 * @date 2023/6/1
 */
public class BookmarkTree extends JTree {

    /**
     * BookmarkTreeNode 缓存，便于通过 UUID 直接取到节点引用
     */
    private final Map<String, BookmarkTreeNode> nodeCache = new HashMap<>();

    private final GroupNavigator navigator = new GroupNavigator(this);

    private DefaultTreeModel model;

    private Project project;

    public BookmarkTree(Project project) {
        super();
        this.project = project;

        BookmarkTreeNode root = new BookmarkTreeNode(new GroupNodeModel("ROOT"));
        model = new DefaultTreeModel(root);
        setModel(model);

        getSelectionModel().setSelectionMode(TreeSelectionModel.CONTIGUOUS_TREE_SELECTION);

        setBorder(JBUI.Borders.empty());
        setBackground(JBColor.WHITE);
        setRootVisible(false);
        setShowsRootHandles(true);

        navigator.activatedGroup = root;

//      TODO 后续需要支持拖拽
        initDragHandler();
//        initSelectionModel();
        initCellRenderer();
        initTreeListeners();
        initContextMenu();


    }

    private void initDragHandler() {
        setDragEnabled(true);
        setDropMode(DropMode.ON_OR_INSERT);
        setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return MOVE;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                BookmarkTree tree = (BookmarkTree) c;
                int[] paths = tree.getSelectionRows();
                if (paths != null && paths.length > 0) {
                    return new NodesTransferable(paths);
                }
                return null;
            }

            @Override
            protected void exportDone(JComponent source, Transferable data, int action) {
                if (action != MOVE) {
                    return;
                }
                BookmarkTree tree = (BookmarkTree) source;
                TreePath[] paths = tree.getSelectionPaths();
                if (paths != null && paths.length > 0) {
                    DefaultTreeModel model = tree.getModel();
                    for (TreePath path : paths) {
                        BookmarkTreeNode node = (BookmarkTreeNode) path.getLastPathComponent();
//                        model.removeNodeFromParent(node);
                    }
                }
            }

            @Override
            public boolean canImport(TransferSupport support) {
                JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
                TreePath destPath = dl.getPath();
                if (destPath == null) {
                    return false;
                }
                BookmarkTreeNode targetNode = (BookmarkTreeNode) destPath.getLastPathComponent();
                return targetNode != null && targetNode.isGroup();
            }

            @Override
            public boolean importData(TransferSupport support) {
                JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
                BookmarkTree tree = (BookmarkTree) support.getComponent();
                TreePath destPath = dl.getPath();
                BookmarkTreeNode targetNode = (BookmarkTreeNode) destPath.getLastPathComponent();

                try {
                    Transferable transferable = support.getTransferable();
                    int[] rows = (int[]) transferable.getTransferData(NodesTransferable.NODES_FLAVOR);
                    DefaultTreeModel model = tree.getModel();

                    List<BookmarkTreeNode> nodes = Arrays.stream(rows)
                            .mapToObj(tree::getNodeForRow)
                            .collect(Collectors.toList());

                    for (BookmarkTreeNode node : nodes) {
                        if (!targetNode.isNodeAncestor(node)) {
                            model.removeNodeFromParent(node);
                            model.insertNodeInto(node, targetNode, targetNode.getChildCount());
                        }
                    }
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return true;
            }
        });
    }

    private void initCellRenderer() {
        setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean isLeaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, isLeaf, row, hasFocus);
                // 如果节点被选中，则设置背景色为透明
                setBackgroundSelectionColor(null);
                setBorderSelectionColor(null);

                BookmarkTreeNode node = (BookmarkTreeNode) value;
                Icon icon = node.isBookmark()
                        ? IconLoader.getIcon("icons/bookmark.svg", BookmarkTree.class)
                        : AllIcons.Nodes.Folder;
                setIcon(icon);

                return this;
            }
        });
    }

    private void initTreeListeners() {
        // 选中监听
        addTreeSelectionListener(event -> {
            int selectionCount = getSelectionCount();
            BookmarkTreeNode selectedNode = (BookmarkTreeNode) getLastSelectedPathComponent();
            if (selectionCount != 1 || null == selectedNode) {
                return;
            }

            if (selectedNode.isGroup()) {
                navigator.activeGroup(selectedNode);
            } else {
                navigator.activeBookmark(selectedNode);
            }
        });

        // 鼠标点击事件
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 双击事件
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    BookmarkTreeNode selectedNode = getEventSourceNode(e);
                    if (selectedNode != null && selectedNode.isBookmark()) {
                        BookmarkNodeModel bookmark = (BookmarkNodeModel) selectedNode.getUserObject();
                        bookmark.getOpenFileDescriptor().navigate(true);
                    }
                }

            }
        });

    }

    /**
     * 初始化右键菜单
     */
    private void initContextMenu() {
        JBPopupMenu popupMenu = new JBPopupMenu();
        JBMenuItem imEdit = new JBMenuItem(I18N.get("bookmark.edit"));
        JBMenuItem imDel = new JBMenuItem(I18N.get("bookmark.delete"));
        JBMenuItem imAddGroup = new JBMenuItem(I18N.get("bookmark.addGroup"));
        // TODO 需要添加可以将某个，目录拉出全局显示标签的按钮
        popupMenu.add(imEdit);
        popupMenu.add(imDel);
        popupMenu.add(imAddGroup);

        imEdit.addActionListener(e -> {
            TreePath path = getSelectionPath();
            if (null == path) {
                return;
            }
            BookmarkTreeNode selectedNode = (BookmarkTreeNode) path.getLastPathComponent();
            if (selectedNode.isBookmark()) {
                BookmarkNodeModel nodeModel = (BookmarkNodeModel) selectedNode.getUserObject();
                Project project = nodeModel.getOpenFileDescriptor().getProject();

                new BookmarkCreatorDialog(project)
                        .defaultName(nodeModel.getName())
                        .defaultDesc(nodeModel.getDesc())
                        .showAndCallback((name, desc) -> {
                            nodeModel.setName(name);
                            nodeModel.setDesc(desc);
                            BookmarkTree.this.model.nodeChanged(selectedNode);
                        });
            }
        });

        imDel.addActionListener(e -> {
            // 获取选定的节点
            TreePath[] selectionPaths = BookmarkTree.this.getSelectionPaths();
            if (selectionPaths == null) {
                return;
            }
            for (TreePath path : selectionPaths) {
                BookmarkTreeNode node = (BookmarkTreeNode) path.getLastPathComponent();
                BookmarkTreeNode parent = (BookmarkTreeNode) node.getParent();
                if (null == parent) {
                    continue;
                }
                this.remove(node);
            }
        });

        imAddGroup.addActionListener(e -> {
            // 获取选定的节点
            BookmarkTreeNode selectedNode = (BookmarkTreeNode) BookmarkTree.this.getLastSelectedPathComponent();
            if (null == selectedNode) {
                return;
            }

            @SuppressWarnings("all")
            InputValidatorEx validatorEx = inputString -> {
                if (StringUtil.isBlank(inputString))
                    return I18N.get("groupNameNonNullMessage");
                return null;
            };

            @SuppressWarnings("all")
            String groupName = Messages.showInputDialog(
                    I18N.get("groupNameInputMessage"),
                    I18N.get("groupName"),
                    null,
                    null,
                    validatorEx
            );

            if (StringUtil.isBlank(groupName)) {
                return;
            }

            BookmarkTreeNode parent;
            if (selectedNode.isGroup()) {
                parent = selectedNode;
            } else {
                parent = (BookmarkTreeNode) selectedNode.getParent();
            }

            // 新的分组节点
            BookmarkTreeNode groupNode = new BookmarkTreeNode(new GroupNodeModel(groupName));
            model.insertNodeInto(groupNode, parent, 0);
        });

        // 右键点击事件
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (!SwingUtilities.isRightMouseButton(e)) {
                    return;
                }
                int row = getClosestRowForLocation(e.getX(), e.getY());
                if (row < 0) {
                    return;
                }
                if (!isRowSelected(row)) {
                    setSelectionRow(row);
                }

                if (row < getRowCount()) {
                    popupMenu.show(BookmarkTree.this, e.getX() + 16, e.getY());
                }
            }
        });
    }


    public BookmarkTreeNode getEventSourceNode(MouseEvent event) {
        int row = getRowForLocation(event.getX(), event.getY());
        return row >= 0
                ? (BookmarkTreeNode) getPathForRow(row).getLastPathComponent()
                : null;
    }

    /**
     * 向当前激活的分组添加指定节点，并刷新树结构
     *
     * @param node 要添加的节点
     */
    public void add(BookmarkTreeNode node) {

        navigator.activatedBookmark = node;
        BookmarkTreeNode parent = navigator.ensureActivatedGroup();

        model.insertNodeInto(node, parent, parent.getChildCount());
        // 定位到新增的节点并使其可见
        scrollPathToVisible(new TreePath(node.getPath()));

        addToCache(node);
    }

    /**
     * 删除指定节点，并刷新树结构
     *
     * @param node 要删除的节点
     */
    public void remove(BookmarkTreeNode node) {
        model.removeNodeFromParent(node);
        removeFromCache(node);
    }

    public BookmarkTreeNode getNodeByModel(BookmarkNodeModel nodeModel) {
        String uuid = nodeModel.getUuid();
        return nodeCache.get(uuid);
    }

    private void addToCache(BookmarkTreeNode node) {
        BookmarkNodeModel userObject = (BookmarkNodeModel) node.getUserObject();
        nodeCache.put(userObject.getUuid(), node);
    }

    /**
     * 递归从缓存删除节点，确保不要内存泄露
     *
     * @param node 递归根节点
     */
    private void removeFromCache(BookmarkTreeNode node) {
        if (node.isBookmark()) {
            BookmarkNodeModel userObject = (BookmarkNodeModel) node.getUserObject();
            nodeCache.remove(userObject.getUuid());
            return;
        }
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            removeFromCache((BookmarkTreeNode) node.getChildAt(i));
        }
    }

    @Override
    public void setModel(TreeModel newModel) {
        this.model = (DefaultTreeModel) newModel;
        Object root = model.getRoot();
        if (root instanceof BookmarkTreeNode) {
            navigator.activatedGroup = (BookmarkTreeNode) root;
        }
        super.setModel(model);
    }

    @Override
    public DefaultTreeModel getModel() {
        return this.model;
    }

    public GroupNavigator getGroupNavigator() {
        return this.navigator;
    }

    public BookmarkTreeNode getNodeForRow(int row) {
        TreePath path = getPathForRow(row);
        if (path != null) {
            return (BookmarkTreeNode) path.getLastPathComponent();
        } else {
            return null;
        }
    }

    /**
     * 标签树的导航器，与快捷键绑定，用于遍历当前选中的分组下的标签，当前分组的下级分组不会被遍历
     */
    public static class GroupNavigator {

        private final BookmarkTree tree;

        private BookmarkTreeNode activatedGroup;
        private BookmarkTreeNode activatedBookmark;

        GroupNavigator(BookmarkTree tree) {
            this.tree = tree;
        }

        public void pre() {
            BookmarkTreeNode group = ensureActivatedGroup();
            if (0 == group.getBookmarkChildCount()) {
                return;
            }

            BookmarkTreeNode bookmark = ensureActivatedBookmark();
            int index = preTreeNodeIndex(group, bookmark);
            navigateTo(index);
        }

        public void next() {
            BookmarkTreeNode group = ensureActivatedGroup();
            if (0 == group.getBookmarkChildCount()) {
                return;
            }

            BookmarkTreeNode bookmark = ensureActivatedBookmark();
            int index = nextTreeNodeIndex(group, bookmark);
            navigateTo(index);
        }

        public void activeGroup(BookmarkTreeNode node) {
            activatedGroup = node;
            if (node.getChildCount() > 0) {
                activatedBookmark = (BookmarkTreeNode) node.getChildAt(0);
            } else {
                activatedBookmark = null;
            }
        }

        public void activeBookmark(BookmarkTreeNode node) {
            activatedBookmark = node;
            activatedGroup = (BookmarkTreeNode) node.getParent();

            TreePath treePath = new TreePath(node.getPath());
            tree.setSelectionPath(treePath);

            if (!tree.isVisible(treePath)) {
                tree.scrollPathToVisible(treePath);
            }
        }

        /**
         * 确保 {@code activatedGroup} 一定是一个在当前树上的节点，
         * 所有读取 {@code activatedGroup} 值的地方都应该调用这个方法，
         * 避免当 {@code activatedGroup} 指向的节点已经从当前的 tree 中移除
         *
         * @return 激活的节点或者根节点
         */
        private BookmarkTreeNode ensureActivatedGroup() {
            if (null == activatedGroup) {
                return (BookmarkTreeNode) tree.getModel().getRoot();
            }
            TreeNode[] path = activatedGroup.getPath();
            int row = tree.getRowForPath(new TreePath(path));
            if (row < 0) {
                return (BookmarkTreeNode) tree.getModel().getRoot();
            }
            return activatedGroup;
        }

        /**
         * 确保 {@code activatedBookmark} 一定是一个在当前树上的节点，
         * 所有读取 {@code activatedBookmark} 值的地方都应该调用这个方法，
         * 避免当 {@code activatedBookmark} 指向的节点已经从当前的 tree 中移除
         *
         * @return 激活的节点 或者 {@code null}
         */
        private BookmarkTreeNode ensureActivatedBookmark() {
            if (null == activatedBookmark) {
                return null;
            }
            TreeNode[] path = activatedBookmark.getPath();
            int row = tree.getRowForPath(new TreePath(path));

            return row < 0 ? null : activatedBookmark;
        }

        private void navigateTo(int index) {
            Validate.isTrue(index >= 0, "index must be greater than 0");

            BookmarkTreeNode nextNode = (BookmarkTreeNode) activatedGroup.getChildAt(index);
            activeBookmark(nextNode);

            BookmarkNodeModel model = (BookmarkNodeModel) nextNode.getUserObject();
            model.getOpenFileDescriptor().navigate(true);
        }

        private int preTreeNodeIndex(BookmarkTreeNode activeGroup, BookmarkTreeNode activatedBookmark) {
            Validate.isTrue(activeGroup.getBookmarkChildCount() > 0, "activeGroup has no child");
            if (null == activatedBookmark) {
                return activeGroup.firstChildIndex();
            }
            int currIndex = activeGroup.getIndex(activatedBookmark);
            int groupSize = activeGroup.getChildCount();

            BookmarkTreeNode node;
            do {
                currIndex = (currIndex - 1 + groupSize) % groupSize;
                node = (BookmarkTreeNode) activeGroup.getChildAt(currIndex);
            } while (node.isGroup());
            return currIndex;
        }

        private int nextTreeNodeIndex(BookmarkTreeNode activeGroup, BookmarkTreeNode activatedBookmark) {
            Validate.isTrue(activeGroup.getBookmarkChildCount() > 0, "activeGroup has no child");
            if (null == activatedBookmark) {
                return activeGroup.firstChildIndex();
            }

            int currIndex = activeGroup.getIndex(activatedBookmark);
            BookmarkTreeNode node;
            do {
                currIndex = (currIndex + 1) % activeGroup.getChildCount();
                node = (BookmarkTreeNode) activeGroup.getChildAt(currIndex);
            } while (node.isGroup());
            return currIndex;
        }

    }

    // 自定义传输对象
    static class NodesTransferable implements Transferable {
        public static final DataFlavor NODES_FLAVOR = new DataFlavor(int[].class, "Tree Rows");

        private final int[] rows;

        public NodesTransferable(int[] rows) {
            this.rows = rows;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{NODES_FLAVOR};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor.equals(NODES_FLAVOR);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (isDataFlavorSupported(flavor)) {
                return rows;
            } else {
                throw new UnsupportedFlavorException(flavor);
            }
        }
    }

}
