package cn.skcks.docking.gb28181.mocking.advice;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;

/**
 * 全局异常处理类
 *
 * @author Shikong
 */
@Slf4j
@ControllerAdvice
public class BasicExceptionAdvice {
    @ExceptionHandler(IOException.class)
    public void exception(HttpServletRequest request, Exception e) {
        if(request.getRequestURI().equals("/video")){
            return;
        }
        e.printStackTrace();
    }
}
