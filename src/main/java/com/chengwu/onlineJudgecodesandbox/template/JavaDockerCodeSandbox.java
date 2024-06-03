package com.chengwu.onlineJudgecodesandbox.template;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.chengwu.onlineJudgecodesandbox.model.ExecuteCodeRequest;
import com.chengwu.onlineJudgecodesandbox.model.ExecuteCodeResponse;
import com.chengwu.onlineJudgecodesandbox.model.ExecuteMessage;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.async.ResultCallbackTemplate;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {

    /**
     * 代码允许允许的最大时间
     * 15秒
     */
    private static final long TIME_OUT = 15 * 1000L;

    /**
     * 镜像全名
     */
    private static final String IMAGE_FULL_NAME = "openjdk:8-alpine";

    /**
     * 容器名称
     */
    private static final String CONTAINER_NAME = "java-code-sandbox-container";


    public static void main(String[] args) {
        JavaDockerCodeSandbox javaNativeCodeSandbox = new JavaDockerCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("2 3", "1 3"));
        String code = ResourceUtil.readStr("testCode/simpleComputer/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }

    /**
     * 3、创建容器，把文件复制到容器内
     *
     * @param userCodeFile
     * @param inputList
     * @return
     */
    @SneakyThrows
    @Override
    public List<ExecuteMessage> runCode(File userCodeFile, List<String> inputList) {
        // 获取默认的 Docker Client
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        // 获取用户代码的父路径
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

        //获取用户代码的父路径的父路径
        String userCodeParentParentPath = userCodeFile.getParentFile().getParentFile().getAbsolutePath();

        // 拉取镜像，并确保镜像一定存在
        makeSureDockerImage(IMAGE_FULL_NAME, dockerClient);

        // 获取容器id，并确保容器一定存在
        String containerId = getContainerId(CONTAINER_NAME, dockerClient, userCodeParentParentPath);


        // docker exec keen_blackwell java -cp /app Main 1 3
        // 执行命令并获取结果
        ArrayList<ExecuteMessage> executeMessageList = new ArrayList<>();


        //获得用户代码的父路径的名字
        String userCodeParentPathName = userCodeParentPath.substring(userCodeParentPath.lastIndexOf("/") + 1);
        for (String inputArgs : inputList) {
            StopWatch stopWatch = new StopWatch();
            // 最大内存占用
            final long[] maxMemory = {0L};
            // 设置执行消息
            ExecuteMessage execDockerMessage;
            final String[] messageDocker = {null};
            final String[] errorDockerMessage = {null};
            long time = 0L;
            execDockerMessage = new ExecuteMessage();
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app/" + userCodeParentPathName, "Main"}, inputArgsArray);
//            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", userCodeParentPath , "Main"}, inputArgsArray);
            System.out.println("执行命令：" + Arrays.toString(cmdArray));
            System.out.println(userCodeParentPath);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStderr(true) // 开启输入输出
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            System.out.println("创建执行命令：" + execCreateCmdResponse.getId());

            String execId = execCreateCmdResponse.getId();
            // 判断超时变量
            final boolean[] isTimeOut = {true};
            if (execId == null) {
                throw new RuntimeException("执行命令不存在");
            }
//            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
//
//                @Override
//                public void onComplete() {
//                    // 执行完成，设置为 false 不超时
//                    isTimeOut[0] = false;
//                    super.onComplete();
//                }
//
//                @Override
//                public void onNext(Frame frame) {
//                    // 获取程序执行信息
//                    StreamType streamType = frame.getStreamType();
//                    if (StreamType.STDERR.equals(streamType)) {
//                        errorDockerMessage[0] = new String(frame.getPayload());
//                        System.out.println("输出错误结果：" + errorDockerMessage[0]);
//                    } else {
//                        messageDocker[0] = new String(frame.getPayload());
//                        System.out.println("输出结果：" + messageDocker[0]);
//                    }
//                    super.onNext(frame);
//                }
//            };

            ResultCallbackTemplate<ResultCallback<Frame>, Frame> resultCallbackTemplate = new ResultCallbackTemplate<ResultCallback<Frame>, Frame>() {
                @Override
                public void onComplete() {
                    // 执行完成，设置为 false 不超时
                    isTimeOut[0] = false;
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    // 获取程序执行信息
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        if (Objects.isNull(errorDockerMessage[0])) {
                            errorDockerMessage[0] = new String(frame.getPayload());
                            System.out.println("输出错误结果：" + errorDockerMessage[0]);
                        } else {
                            errorDockerMessage[0] += new String(frame.getPayload());
                        }

                    } else {
                        if (Objects.isNull(messageDocker[0])) {
                            messageDocker[0] = new String(frame.getPayload());
                            System.out.println("输出结果：" + messageDocker[0]);
                        } else {
                            messageDocker[0] += new String(frame.getPayload());
                        }
                    }
                }
            };


            // 3.5、获取占用的内存
            try (
                    StatsCmd statsCmd = dockerClient.statsCmd(containerId);
                    ResultCallback<Statistics> statisticsResultCallback = new ResultCallback<Statistics>() {
                        @Override
                        public void onNext(Statistics statistics) {
                            Long usageMemory = statistics.getMemoryStats().getUsage();
                            if (usageMemory != null) {
                                maxMemory[0] = Math.max(usageMemory, maxMemory[0]);
                            }
                        }

                        @Override
                        public void onStart(Closeable closeable) {

                        }

                        @Override
                        public void onError(Throwable throwable) {

                        }

                        @Override
                        public void onComplete() {

                        }

                        @Override
                        public void close() {
                            System.out.println("关闭统计");
                            dockerClient.statsCmd(containerId).close();
                        }
                    }) {
                statsCmd.exec(statisticsResultCallback);
                // 执行启动命令
                // 开始前获取时间
                stopWatch.start();
                dockerClient.execStartCmd(execId)
                        .exec(resultCallbackTemplate)
                        .awaitCompletion();
            } catch (
                    InterruptedException e) {
                System.out.println("程序执行异常");
                execDockerMessage.setTime(TIME_OUT);
                executeMessageList.add(execDockerMessage);
                return executeMessageList;
            } finally {
                // 结束计时
                stopWatch.stop();
                // 获取总共时间
                time = stopWatch.getLastTaskTimeMillis();
                // 关闭统计
                System.out.println("关闭统计");
            }
            System.out.println("耗时：" + time + " ms");
            execDockerMessage.setMessage(messageDocker[0]);
            execDockerMessage.setErrorMessage(errorDockerMessage[0]);
            execDockerMessage.setTime(time);
            //如果内存为空，等待
            while (maxMemory[0] == 0) {
                try {
                    System.out.println("等待内存统计");
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("最大内存：" + maxMemory[0]);
            execDockerMessage.setMemory(maxMemory[0]);
            executeMessageList.add(execDockerMessage);
        }
        return executeMessageList;
    }

    /**
     * 确保代码沙箱镜像一定存在
     */
    public void makeSureDockerImage(String imageFullName, DockerClient dockerClient) {
        if (checkImage(imageFullName, dockerClient)) {
            return;
        } else {
            downloadImage(imageFullName, dockerClient);
        }
    }

    /**
     * 检查镜像是否存在
     */
    public Boolean checkImage(String imageFullName, DockerClient dockerClient) {
        // 获取本地所有镜像
        List<Image> images = dockerClient.listImagesCmd().exec();

        // 检查指定镜像是否存在
        boolean imageExists = images.stream().anyMatch(image -> image.getRepoTags() != null && Arrays.asList(image.getRepoTags()).contains(imageFullName));

        if (imageExists) {
            System.out.println(imageFullName + "镜像存在！");
            return true;
        } else {
            System.out.println(imageFullName + "镜像不存在！");
            return false;
        }
    }

    /**
     * 下载镜像文件
     */
    public void downloadImage(String imageFullName, DockerClient dockerClient) {
        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(imageFullName);
        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
            @Override
            public void onNext(PullResponseItem item) {
                System.out.println("下载" + imageFullName + "镜像：" + item.getStatus());
                super.onNext(item);
            }
        };
        try {
            pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
            System.out.println(imageFullName + "镜像下载完成");
        } catch (InterruptedException e) {
            System.out.println("拉取" + imageFullName + "镜像异常");
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取容器id，并确保其一定存在
     */
    public String getContainerId(String containerName, DockerClient dockerClient, String userCodeParentParentPath) {
        // 列出所有容器
        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd().withShowAll(true);
        List<Container> containers = listContainersCmd.exec();

        // 查找指定名称的容器
        Container targetContainer = containers.stream().filter(container -> container.getNames() != null && Arrays.asList(container.getNames()).contains("/" + containerName)).findFirst().orElse(null);

        if (targetContainer != null) {
            String containerId = targetContainer.getId();
            // 获取容器的详细信息
            com.github.dockerjava.api.command.InspectContainerResponse inspectContainerResponse = dockerClient.inspectContainerCmd(containerId).exec();

            // 这里的 inspectContainerResponse 包含了容器的详细信息
            System.out.println("查找到" + containerName + "容器，containerId = " + inspectContainerResponse.getId());
            Boolean running = inspectContainerResponse.getState().getRunning();
            System.out.println("容器状态：" + (running ? "运行中" : "已停止"));
            if (!running) {
                // 启动容器
                dockerClient.startContainerCmd(containerId).exec();
                System.out.println(containerName + "容器启动成功");
            }
            return inspectContainerResponse.getId();
        } else {
            System.out.println("未找到" + containerName + "容器！");
            // 创建容器
            CreateContainerCmd containerCmd = dockerClient.createContainerCmd(IMAGE_FULL_NAME).withName(containerName);
            System.out.println(containerName + "容器创建成功");
            HostConfig hostConfig = new HostConfig();
            hostConfig.withMemory(100 * 1000 * 1000L);
//            hostConfig.withMemorySwap(0L);
            hostConfig.withCpuCount(1L);
            hostConfig.setBinds(new Bind(userCodeParentParentPath, new Volume("/app")));
            CreateContainerResponse createContainerResponse = containerCmd.withHostConfig(hostConfig).withNetworkDisabled(true).withReadonlyRootfs(true).withAttachStdin(true).withAttachStderr(true).withAttachStdout(true).withTty(true).exec();
            System.out.println("创建后的" + containerName + "容器信息：" + createContainerResponse);
            String containerId = createContainerResponse.getId();
            // 启动容器
            dockerClient.startContainerCmd(containerId).exec();
            System.out.println(containerName + "容器启动成功");
            return containerId;
        }
    }
}


