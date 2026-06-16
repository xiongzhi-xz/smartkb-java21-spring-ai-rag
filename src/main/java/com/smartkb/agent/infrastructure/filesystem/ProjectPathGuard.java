package com.smartkb.agent.infrastructure.filesystem;

import com.smartkb.agent.domain.ProjectIntakeException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates local project paths before any file read.
 */
@Component
public class ProjectPathGuard {

    public Path validateProjectRoot(String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            throw new ProjectIntakeException(
                    "PROJECT_PATH_REQUIRED",
                    HttpStatus.BAD_REQUEST,
                    "项目路径不能为空"
            );
        }

        Path path = Path.of(rootPath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new ProjectIntakeException(
                    "PROJECT_PATH_NOT_FOUND",
                    HttpStatus.BAD_REQUEST,
                    "项目路径不存在或不可访问"
            );
        }
        if (!Files.isDirectory(path)) {
            throw new ProjectIntakeException(
                    "PROJECT_PATH_NOT_DIRECTORY",
                    HttpStatus.BAD_REQUEST,
                    "项目路径不是目录"
            );
        }
        if (isSensitivePath(path)) {
            throw new ProjectIntakeException(
                    "PROJECT_PATH_NOT_ALLOWED",
                    HttpStatus.BAD_REQUEST,
                    "项目路径不允许直接指向敏感目录"
            );
        }
        return path;
    }

    public boolean shouldSkip(Path root, Path path) {
        String relative = toRelative(root, path);
        String normalized = relative.replace('\\', '/');
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();

        if (fileName.equals(".env") || fileName.startsWith(".env.")) {
            return true;
        }
        if (fileName.endsWith(".key") || fileName.endsWith(".pem")
                || fileName.endsWith(".p12") || fileName.endsWith(".jks")) {
            return true;
        }
        return normalized.equals(".git")
                || normalized.startsWith(".git/")
                || normalized.equals("target")
                || normalized.startsWith("target/")
                || normalized.equals("build")
                || normalized.startsWith("build/")
                || normalized.equals("node_modules")
                || normalized.startsWith("node_modules/")
                || normalized.startsWith("docker/rocketmq/store")
                || normalized.contains("/store/")
                || normalized.equals("docker-data")
                || normalized.startsWith("docker-data/")
                || normalized.equals("data")
                || normalized.startsWith("data/")
                || normalized.equals("tmp")
                || normalized.startsWith("tmp/")
                || normalized.equals("logs")
                || normalized.startsWith("logs/")
                || normalized.endsWith(".log");
    }

    public String skipReason(Path root, Path path) {
        String relative = toRelative(root, path).replace('\\', '/');
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        if (fileName.equals(".env") || fileName.startsWith(".env.")
                || fileName.endsWith(".key") || fileName.endsWith(".pem")
                || fileName.endsWith(".p12") || fileName.endsWith(".jks")) {
            return "sensitive file";
        }
        if (relative.contains("/store/") || relative.startsWith("docker/rocketmq/store")
                || relative.startsWith("data") || relative.startsWith("docker-data")) {
            return "runtime data";
        }
        if (relative.startsWith("target") || relative.startsWith("build") || relative.startsWith("node_modules")) {
            return "generated output";
        }
        return "ignored path";
    }

    public String toRelative(Path root, Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (absolute.equals(root)) {
            return ".";
        }
        return root.relativize(absolute).toString().replace('\\', '/');
    }

    private boolean isSensitivePath(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        return name.equals(".git") || name.equals("target") || name.equals("node_modules");
    }
}
