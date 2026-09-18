package com.yimo.service;

import com.yimo.common.BizException;
import com.yimo.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 弹出操作系统的原生文件夹选择窗口。
 *
 * <p>为什么这件事必须由后端做：浏览器出于安全考虑，不允许网页获取用户选择的
 * 绝对路径（{@code <input type="file" webkitdirectory>} 只给相对路径）。
 * 后端没有这个限制，可以直接调 Swing 的 {@link JFileChooser}。
 *
 * <p>代价是这个功能只在有图形界面的机器上有效。跑在 Docker 或无头服务器上时，
 * {@link GraphicsEnvironment#isHeadless()} 为 true，会抛
 * {@link ErrorCode#PICKER_UNSUPPORTED}，前端降级为手动输入。
 */
@Service
public class DirectoryPickerService {

    private static final Logger log = LoggerFactory.getLogger(DirectoryPickerService.class);

    /** 同一时间只允许一个选择窗口，防止用户连点弹出多个 */
    private final Semaphore semaphore = new Semaphore(1);

    /**
     * 弹出目录选择窗口，阻塞直到用户选择或取消。
     *
     * @param initialPath 打开时定位到的目录，可为 null
     * @return 选中的绝对路径；用户点了取消则返回 empty
     */
    public Optional<String> pickDirectory(String initialPath) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new BizException(ErrorCode.PICKER_UNSUPPORTED);
        }
        if (!semaphore.tryAcquire()) {
            throw new BizException(ErrorCode.PICKER_BUSY);
        }

        try {
            AtomicReference<File> chosen = new AtomicReference<>();

            // JFileChooser 必须在事件调度线程（EDT）上创建和显示，
            // 直接在 Tomcat 的请求线程里 new 会出各种诡异问题
            SwingUtilities.invokeAndWait(() -> {
                useSystemLookAndFeel();

                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setDialogTitle("选择书库文件夹");
                chooser.setAcceptAllFileFilterUsed(false);
                chooser.setMultiSelectionEnabled(false);

                File initial = toExistingDirectory(initialPath);
                if (initial != null) {
                    chooser.setCurrentDirectory(initial);
                }

                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    chosen.set(chooser.getSelectedFile());
                }
            });

            File file = chosen.get();
            return file == null ? Optional.empty() : Optional.of(file.getAbsolutePath());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.PICKER_FAILED, "等待用户选择时被中断");
        } catch (InvocationTargetException e) {
            log.error("弹出目录选择窗口失败", e.getCause());
            throw new BizException(ErrorCode.PICKER_FAILED,
                    e.getCause() != null ? e.getCause().getMessage() : "未知原因");
        } finally {
            semaphore.release();
        }
    }

    /** 用系统外观，这样窗口长得跟其他 Windows 程序一致，而不是 Swing 默认的金属灰 */
    private void useSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            log.debug("设置系统外观失败，使用默认外观", e);
        }
    }

    private File toExistingDirectory(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        File file = new File(path);
        return file.isDirectory() ? file : null;
    }
}
