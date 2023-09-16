package cn.skcks.docking.gb28181.mocking.service.ffmpeg;

import cn.skcks.docking.gb28181.mocking.config.sip.FfmpegConfig;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FfmpegSupportService {
    private final FfmpegConfig ffmpegConfig;

    @SneakyThrows
    public Executor pushToRtp(String input, String output, long time, TimeUnit unit){
        FfmpegConfig.Rtp rtp = ffmpegConfig.getRtp();
        String inputParam = StringUtils.joinWith(" ", rtp.getInput(), input);
        log.info("视频输入参数 {}", inputParam);

        String outputParam = StringUtils.joinWith(" ", rtp.getOutput(), output);
        log.info("视频输出参数 {}", outputParam);

        String logLevelParam = StringUtils.joinWith(" ","-loglevel", rtp.getLogLevel());
        String command = StringUtils.joinWith(" ", ffmpegConfig.getFfmpeg(), inputParam, outputParam, logLevelParam);
        CommandLine commandLine = CommandLine.parse(command);
        DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
        Executor executor = new DefaultExecutor();
        ExecuteWatchdog watchdog = new ExecuteWatchdog(unit.toMillis(time));
        executor.setExitValue(0);
        executor.setWatchdog(watchdog);
        executor.execute(commandLine, resultHandler);
        return executor;
    }
}
