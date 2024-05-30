package com.chengwu.onlineJudgecodesandbox.template;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.chengwu.onlineJudgecodesandbox.model.ExecuteCodeRequest;
import com.chengwu.onlineJudgecodesandbox.model.ExecuteCodeResponse;
import com.chengwu.onlineJudgecodesandbox.model.ExecuteMessage;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Java调用Docker 实现代码沙箱
 */
@Component
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {

    public static final long TIME_OUT = 5000L;

    // 首次拉取镜像
    public static final Boolean FIRST_INIT = true;

    /**
     * 测试
     *
     * @param args
     */
    public static void main(String[] args) {
        JavaDockerCodeSandbox javaNativeCodeSandBox = new JavaDockerCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3", "1 4 5"));
        String code = ResourceUtil.readStr("testCode/simpleComputer/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandBox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }

    /**
     * 3、创建容器，上传编译文件
     *
     * @param userCodeFile
     * @param inputList
     * @return
     */
    @Override
    public List<ExecuteMessage> runCode(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        // 3.1、拉取镜像
        String image = "openjdk:8-alpine";
        if (FIRST_INIT) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback resultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载拉取镜像中：" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd.exec(resultCallback).awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
        }
        System.out.println("镜像拉取完成");

        // 3.2、创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        // 限制内存
        hostConfig.withMemory(100 * 1000 * 1000L);
        // 设置CPU
        hostConfig.withCpuCount(1L);
        // 内存交换
        hostConfig.withMemorySwap(1000L);
        // 设置安全管理 读写权限
        String profileConfig = ResourceUtil.readUtf8Str("profile.json");
        hostConfig.withSecurityOpts(Collections.singletonList("seccomp=" + profileConfig));
        // 设置容器挂载目录
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        System.out.println("11111111111111111111111111111111111" + userCodeParentPath);
        CreateContainerResponse createContainerResponse = containerCmd
                .withReadonlyRootfs(true)
                .withNetworkDisabled(true) // 禁用网络
                .withHostConfig(hostConfig)
                .withAttachStderr(true) // 开启输入输出
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withTty(true) // 开启一个交互终端
                .exec();
        String containerId = createContainerResponse.getId();
        System.out.println("创建容器id：" + containerId);
        // 3.3、启动容器
        dockerClient.startContainerCmd(containerId).exec();

        // 3.4、执行命令 docker exec containtId java -cp /app Main 1 2
        ArrayList<ExecuteMessage> executeMessageList = new ArrayList<>();

        StopWatch stopWatch = new StopWatch();


        // 设置执行消息
        ExecuteMessage execDockerMessage;
        final String[][] messageDocker = {{null}};
        final String[] errorDockerMessage = {null};
        long time;
        final long[] memory = {0L};


        for (String inputArgs : inputList) {
            execDockerMessage = new ExecuteMessage();

            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
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
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {

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
                        errorDockerMessage[0] = new String(frame.getPayload());
                        System.out.println("输出错误结果：" + errorDockerMessage[0]);
                    } else {
                        messageDocker[0][0] = new String(frame.getPayload());
                        System.out.println("输出结果：" + messageDocker[0][0]);
                    }
                    super.onNext(frame);
                }
            };

            // 3.5、获取占用的内存
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void close() throws IOException {

                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onNext(Statistics statistics) {
                    Long usageMemory = statistics.getMemoryStats().getUsage();
                    System.out.println("内存占用：" + usageMemory);
                    if (usageMemory != null)
                        memory[0] = Math.max(memory[0], usageMemory);
                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }
            });
            statsCmd.exec(statisticsResultCallback);
            try {
                // 执行启动命令
                // 开始前获取时间
                stopWatch.start();
                dockerClient.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                // 结束计时
                stopWatch.stop();
                // 获取总共时间
                time = stopWatch.getLastTaskTimeMillis();
                // 关闭统计
                statsCmd.close();
            } catch (InterruptedException e) {
                System.out.println("程序执行超时");
                execDockerMessage.setErrorMessage("程序执行超时");
                execDockerMessage.setTime(TIME_OUT);
                execDockerMessage.setMemory(memory[0]);
                executeMessageList.add(execDockerMessage);
                return executeMessageList;
            }
            System.out.println("耗时：" + time + " ms");
            execDockerMessage.setMessage(messageDocker[0][0]);
            execDockerMessage.setErrorMessage(errorDockerMessage[0]);
            execDockerMessage.setTime(time);
            execDockerMessage.setMemory(memory[0]);
            executeMessageList.add(execDockerMessage);
        }
        return executeMessageList;
    }
}
