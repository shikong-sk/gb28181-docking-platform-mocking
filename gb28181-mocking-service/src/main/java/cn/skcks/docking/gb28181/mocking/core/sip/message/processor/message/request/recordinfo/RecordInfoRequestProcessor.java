package cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.recordinfo;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.skcks.docking.gb28181.common.xml.XmlUtils;
import cn.skcks.docking.gb28181.core.sip.message.processor.message.types.recordinfo.query.dto.RecordInfoRequestDTO;
import cn.skcks.docking.gb28181.mocking.config.sip.DeviceProxyConfig;
import cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.recordinfo.dto.RecordInfoItemDTO;
import cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.recordinfo.dto.RecordInfoResponseDTO;
import cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.recordinfo.dto.RecordListDTO;
import cn.skcks.docking.gb28181.mocking.core.sip.request.SipRequestBuilder;
import cn.skcks.docking.gb28181.mocking.core.sip.response.SipResponseBuilder;
import cn.skcks.docking.gb28181.mocking.core.sip.sender.SipSender;
import cn.skcks.docking.gb28181.mocking.core.sip.service.VideoCacheManager;
import cn.skcks.docking.gb28181.mocking.orm.mybatis.dynamic.model.MockingDevice;
import cn.skcks.docking.gb28181.mocking.service.device.DeviceService;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.sip.header.CallIdHeader;
import javax.sip.header.FromHeader;
import javax.sip.message.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class RecordInfoRequestProcessor {
    private final SipSender sender;
    private final DeviceService deviceService;
    private final DeviceProxyConfig deviceProxyConfig;
    private final VideoCacheManager videoCacheManager;

    public void process(SIPRequest request, byte[] content) {
        String senderIp = request.getLocalAddress().getHostAddress();
        String transport = request.getTopmostViaHeader().getTransport();
        RecordInfoRequestDTO recordInfoRequestDTO = XmlUtils.parse(content, RecordInfoRequestDTO.class);
        String id = recordInfoRequestDTO.getDeviceId();
        deviceService.getDeviceByGbChannelId(id).ifPresentOrElse((device) -> {
            if(preDownloadVideo(device.getDeviceCode(), recordInfoRequestDTO)){
                sendRecordInfo(device, recordInfoRequestDTO, request, senderIp, transport);
            } else {
                sendEmptyRecordInfo(device, recordInfoRequestDTO, request, senderIp, transport);
            }
        }, () -> {
            deviceService.getDeviceByGbChannelId(id).ifPresentOrElse((device) -> {
                if(preDownloadVideo(device.getDeviceCode(), recordInfoRequestDTO)){
                    sendRecordInfo(device, recordInfoRequestDTO, request, senderIp, transport);
                } else {
                    sendEmptyRecordInfo(device, recordInfoRequestDTO, request, senderIp, transport);
                }
            }, () -> {
                log.error("未能找到 deviceId: {} 的相关信息", id);
                sender.sendResponse(senderIp, transport, notFound(request));
            });
        });
    }

    private boolean preDownloadVideo(String deviceCode, RecordInfoRequestDTO recordInfoRequestDTO){
        if(!deviceProxyConfig.getPreDownloadForRecordInfo().getEnable()){
            return true;
        }

        Date startTime = recordInfoRequestDTO.getStartTime();
        Date endTime = recordInfoRequestDTO.getEndTime();
        if(DateUtil.between(startTime,endTime, DateUnit.SECOND) > deviceProxyConfig.getProxyTimeRange().getSeconds()){
            return true;
        }

        // 添加预下载任务
        videoCacheManager.addTask(deviceCode,startTime,endTime);

        return videoCacheManager.get(deviceCode,startTime,endTime).isDone();
    }

    private void sendRecordInfo(MockingDevice device, RecordInfoRequestDTO recordInfoRequestDTO, SIPRequest request, String senderIp, String transport) {
        sender.sendResponse(senderIp, transport, ok(request));
        Date startTime = recordInfoRequestDTO.getStartTime();
        Date endTime = recordInfoRequestDTO.getEndTime();
        String name = StringUtils.joinWith(" - ",
                DateUtil.format(startTime, DatePattern.NORM_DATETIME_FORMATTER),
                DateUtil.format(endTime, DatePattern.NORM_DATETIME_FORMATTER));

        List<RecordInfoItemDTO> recordInfoItemDTOList = new ArrayList<>();
        Date tmpStart = startTime;
        Date tmpEnd = DateUtil.offsetMinute(tmpStart,5);
        while(DateUtil.compare(tmpStart, endTime) < 0){
            RecordInfoItemDTO recordInfoItemDTO = new RecordInfoItemDTO();
            recordInfoItemDTO.setName(name);
            recordInfoItemDTO.setStartTime(tmpStart);
            recordInfoItemDTO.setEndTime(tmpEnd);
            recordInfoItemDTO.setSecrecy(recordInfoRequestDTO.getSecrecy());
            recordInfoItemDTO.setDeviceId(device.getGbChannelId());
            recordInfoItemDTOList.add(recordInfoItemDTO);

            tmpStart = tmpEnd;
            tmpEnd = DateUtil.offsetMinute(tmpStart,5);
        }

        RecordInfoItemDTO recordInfoItemDTO = new RecordInfoItemDTO();
        recordInfoItemDTO.setName(name);
        recordInfoItemDTO.setStartTime(startTime);
        recordInfoItemDTO.setEndTime(endTime);
        recordInfoItemDTO.setSecrecy(recordInfoRequestDTO.getSecrecy());
        recordInfoItemDTO.setDeviceId(device.getGbChannelId());

        FromHeader fromHeader = request.getFromHeader();
        ListUtil.partition(recordInfoItemDTOList,50).forEach(recordList->{
            RecordInfoResponseDTO recordInfoResponseDTO = new RecordInfoResponseDTO();
            recordInfoResponseDTO.setSn(recordInfoRequestDTO.getSn());
            recordInfoResponseDTO.setDeviceId(device.getGbChannelId());
            recordInfoResponseDTO.setName(device.getName());
            recordInfoResponseDTO.setSumNum((long) recordInfoItemDTOList.size());
            RecordListDTO recordListDTO = RecordListDTO.builder()
                    .recordList(recordList)
                    .num(recordList.size())
                    .build();
            recordInfoResponseDTO.setRecordList(recordListDTO);

            sender.sendRequest((provider, ip, port) -> {
                CallIdHeader callIdHeader = provider.getNewCallId();
                return SipRequestBuilder.createMessageRequest(device,
                        ip, port, 1, XmlUtils.toXml(recordInfoResponseDTO), fromHeader.getTag(), callIdHeader);
            });
        });
    }

    private void sendEmptyRecordInfo(MockingDevice device, RecordInfoRequestDTO recordInfoRequestDTO, SIPRequest request, String senderIp, String transport) {
        sender.sendResponse(senderIp, transport, ok(request));

        RecordInfoResponseDTO recordInfoResponseDTO = new RecordInfoResponseDTO();
        recordInfoResponseDTO.setSn(recordInfoRequestDTO.getSn());
        recordInfoResponseDTO.setDeviceId(device.getGbChannelId());
        recordInfoResponseDTO.setName(device.getName());
        recordInfoResponseDTO.setSumNum(0L);
        RecordListDTO recordListDTO = RecordListDTO.builder()
                .recordList(Collections.emptyList())
                .num(0)
                .build();
        recordInfoResponseDTO.setRecordList(recordListDTO);

        FromHeader fromHeader = request.getFromHeader();
        sender.sendRequest((provider, ip, port) -> {
            CallIdHeader callIdHeader = provider.getNewCallId();
            return SipRequestBuilder.createMessageRequest(device,
                    ip, port, 1, XmlUtils.toXml(recordInfoResponseDTO), fromHeader.getTag(), callIdHeader);
        });
    }

    private SipSender.SendResponse ok(SIPRequest request) {
        return (provider, ip, port) -> SipResponseBuilder.response(request, Response.OK,
                "OK");
    }

    private SipSender.SendResponse notFound(SIPRequest request) {
        return (provider, ip, port) -> SipResponseBuilder.response(request, Response.NOT_FOUND,
                "Not Found");
    }
}
