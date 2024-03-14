package cn.skcks.docking.gb28181.mocking.service.ffmpeg;

import cn.skcks.docking.gb28181.mocking.config.sip.FfmpegConfig;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FfmpegSupportService {
    private final FfmpegConfig ffmpegConfig;

    @SneakyThrows
    public Executor pushToRtp(String input, String output, long time, TimeUnit unit,ExecuteResultHandler resultHandler){
        FfmpegConfig.Rtp rtp = ffmpegConfig.getRtp();
        FfmpegConfig.Debug debug = ffmpegConfig.getDebug();
        String inputParam = debug.getInput() ? rtp.getInput() : StringUtils.joinWith(" ", rtp.getInput(), "\"" + input + "\"");
        log.info("视频输入参数 {}", inputParam);

        String outputParam = debug.getOutput()? rtp.getOutput() : StringUtils.joinWith(" ", rtp.getOutput(), "\"" + output + "\"");
        log.info("视频输出参数 {}", outputParam);

        return ffmpegExecutor(inputParam, outputParam, time, unit, resultHandler);
    }

    @SneakyThrows
    public Executor pushDownload2Rtp(String input, String output, long time, TimeUnit unit, ExecuteResultHandler resultHandler){
        FfmpegConfig.Rtp rtp = ffmpegConfig.getRtp();
        FfmpegConfig.Debug debug = ffmpegConfig.getDebug();
        String inputParam;
        if(rtp.getDownloadSpeed() > 0){
            String downloadSpeed = StringUtils.joinWith(" ","-filter:v", MessageFormat.format("\"setpts=1/{0}*PTS\"",rtp.getDownloadSpeed()));
            inputParam = debug.getDownload()? rtp.getDownload() : StringUtils.joinWith(" ", rtp.getDownload(), input, downloadSpeed);
        } else {
            inputParam = debug.getDownload()? rtp.getDownload(): StringUtils.joinWith(" ", rtp.getDownload(), input);
        }

        log.info("视频下载参数 {}", inputParam);

        String outputParam = debug.getOutput()? rtp.getOutput() : StringUtils.joinWith(" ", "-t", unit.toSeconds(time), rtp.getOutput(), output);
        log.info("视频输出参数 {}", outputParam);

        return ffmpegExecutor(inputParam, outputParam, time + 60, unit, resultHandler);
    }

    @SneakyThrows
    public Executor ffmpegExecutor(String inputParam,String outputParam, long time, TimeUnit unit,ExecuteResultHandler resultHandler){
        FfmpegConfig.Rtp rtp = ffmpegConfig.getRtp();
        String logLevelParam = StringUtils.joinWith(" ","-loglevel", rtp.getLogLevel());
        String command = StringUtils.joinWith(" ", ffmpegConfig.getFfmpeg(), logLevelParam, inputParam, outputParam);
        CommandLine commandLine = CommandLine.parse(command);
        Executor executor = new DefaultExecutor();
        ExecuteWatchdog watchdog = new ExecuteWatchdog(unit.toMillis(time));
        executor.setWatchdog(watchdog);
        executor.execute(commandLine, resultHandler);
        return executor;
    }
}
