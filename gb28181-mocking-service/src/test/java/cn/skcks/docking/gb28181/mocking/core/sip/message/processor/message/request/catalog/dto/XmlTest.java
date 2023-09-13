package cn.skcks.docking.gb28181.mocking.core.sip.message.processor.message.request.catalog.dto;


import cn.skcks.docking.gb28181.common.xml.XmlUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class XmlTest {
    @Test
    void test(){
        CatalogResponseDTO catalogResponseDTO = new CatalogResponseDTO();

        CatalogDeviceListDTO catalogDeviceListDTO = new CatalogDeviceListDTO();
        List<CatalogItemDTO> itemDTOList = new ArrayList<>();
        itemDTOList.add(CatalogItemDTO.builder().build());
        itemDTOList.add(CatalogItemDTO.builder().build());
        catalogDeviceListDTO.setDeviceList(itemDTOList);
        catalogDeviceListDTO.setNum(itemDTOList.size());

        catalogResponseDTO.setDeviceList(catalogDeviceListDTO);
        String xml = XmlUtils.toXml(catalogResponseDTO);
        log.info("{}", xml);

        log.info("{}", XmlUtils.toXml(catalogDeviceListDTO));
    }
}
