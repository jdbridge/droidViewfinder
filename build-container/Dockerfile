# Android build container for droidViewfinder: JDK + Android command-line tools + adb. Nothing project-specific
# is baked in -- Gradle, AGP and Kotlin come from the project's own wrapper and plugin versions.
# Versions verified against the official release pages on 2026-09-24; see README.md for how to bump them.
ARG JDK_IMAGE=eclipse-temurin:25-jdk-noble
FROM ${JDK_IMAGE}

# Command-line tools: https://developer.android.com/studio#command-line-tools-only
ARG CMDLINE_TOOLS_VERSION=15859902
ARG CMDLINE_TOOLS_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
# API 37 = Android 17 platform; build-tools 36.0.0 is AGP 9.4's required minimum/default.
ARG SDK_PLATFORM=platforms;android-37.0
ARG SDK_BUILD_TOOLS=build-tools;36.0.0
ARG USER_UID=1000
ARG USER_GID=1000

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/home/dev/.gradle
ENV PATH="${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools"

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip curl git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# The base image may already own uid/gid 1000 (noble's "ubuntu"); rename it rather than collide, so the
# in-container "dev" user matches the host user that owns the bind-mounted checkout.
RUN if getent group ${USER_GID} >/dev/null; then \
      groupmod -n dev "$(getent group ${USER_GID} | cut -d: -f1)"; \
    else \
      groupadd -g ${USER_GID} dev; \
    fi \
    && if getent passwd ${USER_UID} >/dev/null; then \
      usermod -l dev -d /home/dev -m -g ${USER_GID} -s /bin/bash "$(getent passwd ${USER_UID} | cut -d: -f1)"; \
    else \
      useradd -m -u ${USER_UID} -g ${USER_GID} -s /bin/bash dev; \
    fi \
    && mkdir -p ${ANDROID_HOME} /home/dev/.gradle /home/dev/.android \
    && chown -R dev:dev ${ANDROID_HOME} /home/dev

RUN curl -fsSL -o /tmp/cmdline-tools.zip \
      "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
    && echo "${CMDLINE_TOOLS_SHA256}  /tmp/cmdline-tools.zip" | sha256sum -c - \
    && mkdir -p ${ANDROID_HOME}/cmdline-tools \
    && unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools \
    && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
    && rm /tmp/cmdline-tools.zip \
    && chown -R dev:dev ${ANDROID_HOME}

COPY --chmod=0755 container-init.sh /usr/local/bin/container-init.sh

USER dev
WORKDIR /workspace

# Pre-install the platform and build-tools so the first build doesn't download them into the SDK.
# (`yes | sdkmanager` exits non-zero on SIGPIPE, hence the explicit checks below.)
RUN yes | sdkmanager --licenses >/dev/null 2>&1 || true
RUN sdkmanager --install "platform-tools" "${SDK_PLATFORM}" "${SDK_BUILD_TOOLS}" \
    && test -x ${ANDROID_HOME}/platform-tools/adb \
    && test -d ${ANDROID_HOME}/build-tools/36.0.0

ENTRYPOINT ["/usr/local/bin/container-init.sh"]
CMD ["sleep", "infinity"]
