# VENDOR 说明

本目录为 zxing-cpp 的第三方源码快照，请勿直接修改其中代码（升级时整目录替换）。

- 来源：https://github.com/zxing-cpp/zxing-cpp
- 版本 tag：**v3.1.1**
- 引入方式：GitHub 源码 tarball（`https://codeload.github.com/zxing-cpp/zxing-cpp/tar.gz/refs/tags/v3.1.1`），整树入库
- 引入日期：2026-09-29

## zint 子模块（编码器）

`zint/` 是上游的 git submodule（GitHub 自动源码包不含子模块，需单独拉取）：

- 来源：https://github.com/zint/zint，commit `55541e139e62b9209b71cd9b0ba9010cec28b1d9`（v3.1.1 钉定的版本）
- 仅保留 `backend/`（`core/src/libzint/*.c` 是 include 桩，转发到 `zint/backend/*.c`）+ LICENSE

## 相对上游的裁剪

为减小体积删除了与 Android 构建无关的目录：

- `test/`、`example/`、`wrappers/`、`.github/`、`Package.swift`、`zxing-cpp.podspec`、`Commercial Support.md`

保留了 `docs/`（根 `CMakeLists.txt` 无条件 `add_subdirectory(docs)`）、`zxing.cmake`（根 CMakeLists 无条件 `include`）。

注意：`wrappers/` 被删除后，构建时必须 `ZXING_C_API=OFF`（否则 `add_subdirectory(wrappers/c)` 失败）。本工程 `scanner-zxing/src/main/cpp/CMakeLists.txt` 已如此设置。

## 构建选项（见上层 CMakeLists）

`ZXING_READERS=ON`、`ZXING_WRITERS=ON`（新版 writer，基于自带的 `core/src/libzint`，`ZXING_USE_BUNDLED_ZINT` 默认 ON，无需联网 FetchContent）、`ZXING_EXAMPLES=OFF`、`ZXING_UNIT_TESTS=OFF`、`ZXING_BLACKBOX_TESTS=OFF`、`BUILD_SHARED_LIBS=OFF`（静态核心链接进 `libZXingCppDecoder.so`）。
