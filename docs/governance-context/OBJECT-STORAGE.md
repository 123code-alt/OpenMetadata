# 文档持久化：S3 / MinIO

文章和记忆保存在 PG，文档二进制保存在 S3 兼容存储；PG 元数据和 S3 对象需要一起备份。原版 InMemory / NoOp 不用于持久化部署。

## 复用现有 S3

1. 新建专用 bucket `governance-context-v208`，保留标准环境原 bucket。
2. 为应用创建仅限该 bucket 的读、写、列举和按业务需要删除对象的权限。
3. 按 `governance-context/windows.env.example` 填写 endpoint、region、bucket、访问密钥和秘密密钥，prefix 为 `governance-context-v208`。
4. 启动后在上下文中心上传文本文件，下载后比较内容；重启 Java 服务再下载同一文件。

endpoint 必须是 S3 API 地址，MinIO 通常为 9000；不是 Console 的 9001。云端验证已通过原版 ContextFile API 上传、鉴权下载和字节一致性检查。

## 没有 S3：独立准备 MinIO

依赖服务可使用 Docker，也可在其他机器提供。使用你已确认可拉取的、固定版本的 MinIO 镜像，并持久化 `/data`；不要把文档只放在容器可写层。本轮环境中 MinIO 下载站二进制返回 410，尝试的 registry 镜像无法拉取，因此没有把这些失败地址写成保证可用的安装命令。

云端实际采用 **官方源码固定 tag `RELEASE.2025-10-15T17-29-55Z` + Go 1.24.13** 构建 Linux 原生服务，通过了 S3 联合检查。MinIO 是单独部署的 AGPL 服务，不合并进应用 ZIP。

如果需要 Windows 原生构建，下面是显式步骤；**该 Windows 构建尚未实测**。已有 S3 则跳过整段。准备 Go 1.24.13 或满足该 tag go.mod 的工具链，确认实际 `go.exe` 是 Go 编译器：

```powershell
go version
git clone --depth 1 --branch RELEASE.2025-10-15T17-29-55Z https://github.com/minio/minio.git C:\tools\minio-src
Set-Location C:\tools\minio-src
go build -o C:\tools\minio.exe .
New-Item -ItemType Directory -Force C:\om-governance\minio-data
```

在单独终端设置仅本机使用的初始管理凭据，前台启动：

```powershell
$env:MINIO_ROOT_USER = '替换为本机管理员用户名'
$env:MINIO_ROOT_PASSWORD = '替换为高强度管理员密码'
C:\tools\minio.exe server C:\om-governance\minio-data --address 127.0.0.1:9000 --console-address 127.0.0.1:9001
```

保持窗口开启。若构建或该版本 Windows 支持遇到问题，使用已有 S3 或在 Linux 上单独部署 MinIO；应用配置接口相同。

安装官方 AWS CLI v2 后，在另一个终端配置本机管理 profile，凭据在交互中填写，不提交配置文件：

```powershell
aws configure --profile governance-context-admin
aws --profile governance-context-admin --endpoint-url http://127.0.0.1:9000 s3api create-bucket --bucket governance-context-v208
aws --profile governance-context-admin --endpoint-url http://127.0.0.1:9000 s3api head-bucket --bucket governance-context-v208
```

region 可填 `us-east-1`。再通过 MinIO 管理能力创建应用专用凭据并限制到该 bucket，将应用凭据填入 IDEA，管理凭据仅用于管理。若你的 MinIO 版本没有 Console 管理页，使用其对应版本的 `mc` 管理客户端或已有运维平台完成授权；不把管理密钥当作长期应用凭据。
