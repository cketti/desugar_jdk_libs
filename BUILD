alias(
    name = "desugar_jdk_libs",
    actual = "//src/share/classes/java",
    visibility = ["//visibility:public"],
)

alias(
    name = "desugar_jdk_libs_jdk11",
    actual = "//jdk11/src:d8_java_base_selected_with_addon",
    visibility = ["//visibility:public"],
)

genrule(
    name = "maven_release",
    srcs = [
        ":desugar_jdk_libs",
        "VERSION.txt",
        "DEPENDENCIES.txt",
    ],
    outs = ["desugar_jdk_libs.zip"],
    cmd = "$(location :build_maven_artifact)" +
          " --jar $(location :desugar_jdk_libs)" +
          " --artifact_id desugar_jdk_libs" +
          " --version_file $(location VERSION.txt)" +
          " --dependencies_file $(location DEPENDENCIES.txt)" +
          " --out $@",
    exec_tools = [":build_maven_artifact"],
)

genrule(
    name = "maven_release_jdk11_legacy",
    srcs = [
        ":desugar_jdk_libs_jdk11",
        "VERSION_JDK11_LEGACY.txt",
        "DEPENDENCIES_JDK11_LEGACY.txt",
    ],
    outs = ["desugar_jdk_libs_jdk11_legacy.zip"],
    cmd = "$(location :build_maven_artifact)" +
          " --jar $(location :desugar_jdk_libs_jdk11)" +
          " --artifact_id desugar_jdk_libs" +
          " --version_file $(location VERSION_JDK11_LEGACY.txt)" +
          " --dependencies_file $(location DEPENDENCIES_JDK11_LEGACY.txt)" +
          " --out $@",
    exec_tools = [":build_maven_artifact"],
)

genrule(
    name = "maven_release_jdk11_minimal",
    srcs = [
        ":desugar_jdk_libs_jdk11",
        "VERSION_JDK11_MINIMAL.txt",
        "DEPENDENCIES_JDK11_MINIMAL.txt",
    ],
    outs = ["desugar_jdk_libs_jdk11_minimal.zip"],
    cmd = "$(location :build_maven_artifact)" +
          " --jar $(location :desugar_jdk_libs_jdk11)" +
          " --artifact_id desugar_jdk_libs_minimal" +
          " --version_file $(location VERSION_JDK11_MINIMAL.txt)" +
          " --dependencies_file $(location DEPENDENCIES_JDK11_MINIMAL.txt)" +
          " --out $@",
    exec_tools = [":build_maven_artifact"],
)

genrule(
    name = "maven_release_jdk11",
    srcs = [
        ":desugar_jdk_libs_jdk11",
        "VERSION_JDK11.txt",
        "DEPENDENCIES_JDK11.txt",
    ],
    outs = ["desugar_jdk_libs_jdk11.zip"],
    cmd = "$(location :build_maven_artifact)" +
          " --jar $(location :desugar_jdk_libs_jdk11)" +
          " --artifact_id desugar_jdk_libs" +
          " --version_file $(location VERSION_JDK11.txt)" +
          " --dependencies_file $(location DEPENDENCIES_JDK11.txt)" +
          " --out $@",
    exec_tools = [":build_maven_artifact"],
)

genrule(
    name = "maven_release_jdk11_nio",
    srcs = [
        ":desugar_jdk_libs_jdk11",
        "VERSION_JDK11_NIO.txt",
        "DEPENDENCIES_JDK11_NIO.txt",
    ],
    outs = ["desugar_jdk_libs_jdk11_nio.zip"],
    cmd = "$(location :build_maven_artifact)" +
          " --jar $(location :desugar_jdk_libs_jdk11)" +
          " --artifact_id desugar_jdk_libs_nio" +
          " --version_file $(location VERSION_JDK11_NIO.txt)" +
          " --dependencies_file $(location DEPENDENCIES_JDK11_NIO.txt)" +
          " --out $@",
    exec_tools = [":build_maven_artifact"],
)

py_binary(
    name = "build_maven_artifact",
    srcs = ["tools/build_maven_artifact.py"],
    python_version = "PY3",
    srcs_version = "PY3",
)

alias(
    name = "tools/jdk_type_selector",
    actual = "//tools/java/com/google/devtools/build/android:jdk_type_selector",
    visibility = ["//visibility:public"],
)
