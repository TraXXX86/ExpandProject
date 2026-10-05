#!/usr/bin/env sh
set -eu

MAVEN_USER_HOME=${MAVEN_USER_HOME:-${XDG_CACHE_HOME:-${HOME}/.m2}}
WRAPPER_DIR=${MAVEN_USER_HOME}/wrapper/dists/apache-maven-3.9.9
MAVEN_HOME=${WRAPPER_DIR}/apache-maven-3.9.9
ARCHIVE=${WRAPPER_DIR}/apache-maven-3.9.9-bin.tar.gz
EXPECTED_SHA512=a555254d6b53d267965a3404ecb14e53c3827c09c3b94b5678835887ab404556bfaf78dcfe03ba76fa2508649dca8531c74bca4d5846513522404d48e8c4ac8b

if [ ! -x "${MAVEN_HOME}/bin/mvn" ]; then
  mkdir -p "${WRAPPER_DIR}"
  if [ ! -f "${ARCHIVE}" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl --fail --location --silent --show-error \
        https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.tar.gz \
        --output "${ARCHIVE}"
    elif command -v wget >/dev/null 2>&1; then
      wget --quiet --output-document="${ARCHIVE}" \
        https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.tar.gz
    else
      echo "mvnw needs curl or wget to download Maven 3.9.9" >&2
      exit 1
    fi
  fi
  if command -v sha512sum >/dev/null 2>&1; then
    ACTUAL_SHA512=$(sha512sum "${ARCHIVE}" | cut -d ' ' -f 1)
  elif command -v shasum >/dev/null 2>&1; then
    ACTUAL_SHA512=$(shasum -a 512 "${ARCHIVE}" | cut -d ' ' -f 1)
  else
    echo "mvnw needs sha512sum or shasum to verify the Maven download" >&2
    exit 1
  fi
  if [ "${ACTUAL_SHA512}" != "${EXPECTED_SHA512}" ]; then
    rm -f "${ARCHIVE}"
    echo "Maven 3.9.9 archive checksum verification failed" >&2
    exit 1
  fi
  tar -xzf "${ARCHIVE}" -C "${WRAPPER_DIR}"
fi
exec "${MAVEN_HOME}/bin/mvn" "$@"
