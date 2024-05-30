package com.chengwu.onlineJudgecodesandbox.template;

import cn.hutool.json.JSONUtil;
import com.chengwu.onlineJudgecodesandbox.model.ExecuteCodeRequest;
import com.chengwu.onlineJudgecodesandbox.model.ExecuteCodeResponse;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 原生Java代码沙箱 - 实现模板方法
 */
@Component
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate {
    /**
     * 执行程序
     * @param executeCodeRequest
     * @return
     */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }
    public static void main(String[] args) {
        final ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setCode("public class Main { public static void main(String[] args) { System.out.println(args[0]); } }");
        executeCodeRequest.setLanguage("java");
        executeCodeRequest.setInputList(Arrays.asList("1", "2", "31231"));
        JavaNativeCodeSandbox javaCodeSandbox = new JavaNativeCodeSandbox();
        ExecuteCodeResponse executeCodeResponse = javaCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(JSONUtil.toJsonStr(executeCodeResponse));
    }
}
