package com.chengwu.onlineJudgecodesandbox.controller;

import com.chengwu.onlineJudgecodesandbox.constants.AuthRequest;
import com.chengwu.onlineJudgecodesandbox.model.ExecuteCodeRequest;
import com.chengwu.onlineJudgecodesandbox.model.ExecuteCodeResponse;
import com.chengwu.onlineJudgecodesandbox.template.JavaDockerCodeSandbox;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/")
public class ExecutedController {

    @Resource
    private JavaDockerCodeSandbox javaDockerCodeSandbox;
    /**
     * 执行代码接口
     */
    @PostMapping("/executeCode")
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest,
                                           HttpServletRequest request, HttpServletResponse response) {
        String authHeader = request.getHeader(AuthRequest.AUTH_REQUEST_HEADER);
        // 基本的认证
        if (!AuthRequest.AUTH_REQUEST_SECRET.equals(authHeader)) {
            // 不匹配则禁止
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数错误");
        }
//        return javaNativeCodeSandbox.executeCode(executeCodeRequest);
        return javaDockerCodeSandbox.executeCode(executeCodeRequest);
    }

}
