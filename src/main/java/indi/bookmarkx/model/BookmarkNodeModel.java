package indi.bookmarkx.model;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.Icon;
import java.util.Objects;
import java.util.Optional;

/**
 * 书签数据模型
 *
 * @author Nonoas
 * @date 2023/6/4
 */
public class BookmarkNodeModel extends AbstractTreeNodeModel {

    private String uuid;

    private int index;
    private int line;

    private Icon icon;

    /**
     * 文件跳转器
     */
    private OpenFileDescriptor openFileDescriptor;

    public BookmarkNodeModel() {
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BookmarkNodeModel yourClass = (BookmarkNodeModel) o;
        return uuid.equals(yourClass.uuid);
    }

    public OpenFileDescriptor getOpenFileDescriptor() {
        return openFileDescriptor;
    }

    public void setOpenFileDescriptor(OpenFileDescriptor openFileDescriptor) {
        this.openFileDescriptor = openFileDescriptor;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getLine() {
        return line;
    }

    /**
     * 设置当前值时，会同步更新 {@link BookmarkNodeModel#openFileDescriptor}
     *
     * @param newLine 新值
     */
    public void setLine(int newLine) {
        this.line = newLine;
    }

    public Icon getIcon() {
        return icon;
    }

    public void setIcon(Icon icon) {
        this.icon = icon;
    }

    public String getUuid() {
        return uuid;
    }

    @Override
    public final boolean isBookmark() {
        return true;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Optional<String> getFilePath() {
        return Optional.ofNullable(openFileDescriptor)
                .map(OpenFileDescriptor::getFile)
                .map(VirtualFile::getPath);
    }
}
