# syntax=docker/dockerfile:1.7
FROM node:24-alpine AS build
WORKDIR /app

COPY ui/package.json ui/package-lock.json ./
RUN --mount=type=secret,id=proxy_ca,target=/tmp/proxy_ca.crt,required=false \
    if [ -f /tmp/proxy_ca.crt ]; then \
      NODE_EXTRA_CA_CERTS=/tmp/proxy_ca.crt npm ci; \
    else \
      npm ci; \
    fi

COPY ui/ ./

ARG VITE_API_BASE=
ENV VITE_API_BASE=${VITE_API_BASE}

RUN --mount=type=secret,id=proxy_ca,target=/tmp/proxy_ca.crt,required=false \
    if [ -f /tmp/proxy_ca.crt ]; then \
      NODE_EXTRA_CA_CERTS=/tmp/proxy_ca.crt npm run build; \
    else \
      npm run build; \
    fi

FROM nginx:stable-alpine
COPY ui/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
