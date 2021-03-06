/*
 * Copyright (c) 2010-2015, Isode Limited, London, England.
 * All rights reserved.
 */
package com.isode.stroke.streamstack;

import com.isode.stroke.base.SafeByteArray;
import com.isode.stroke.elements.Element;
import com.isode.stroke.elements.ProtocolHeader;
import com.isode.stroke.elements.StreamType;
import com.isode.stroke.parser.PayloadParserFactoryCollection;
import com.isode.stroke.parser.XMPPParser;
import com.isode.stroke.parser.XMPPParserClient;
import com.isode.stroke.serializer.PayloadSerializerCollection;
import com.isode.stroke.serializer.XMPPSerializer;
import com.isode.stroke.signals.Signal;
import com.isode.stroke.signals.Signal1;

/**
 * This uses the inner StreamLayer to work around the HighLayer not having
 * implementations because of the lack of multiple inheritance.
 * Swiften doesn't require an eventLoop, Stroke does because of
 * XML parsing being multi-threaded here.
 */
public class XMPPLayer implements HighLayer, XMPPParserClient {
    public final Signal1<ProtocolHeader> onStreamStart = new Signal1<ProtocolHeader>();
    public final Signal1<Element> onElement = new Signal1<Element>();
    public final Signal1<SafeByteArray> onWriteData = new Signal1<SafeByteArray>();
    public final Signal1<SafeByteArray> onDataRead = new Signal1<SafeByteArray>();
    public final Signal onError = new Signal();

    private PayloadParserFactoryCollection payloadParserFactories_;
    private XMPPParser xmppParser_;
    private PayloadSerializerCollection payloadSerializers_;
    private XMPPSerializer xmppSerializer_;
    private boolean resetParserAfterParse_;
    private boolean inParser_;
    private boolean setExplictNSonTopLevelElements_;

    public XMPPLayer(
            PayloadParserFactoryCollection payloadParserFactories,
            PayloadSerializerCollection payloadSerializers,
            StreamType streamType) {
        this(payloadParserFactories, payloadSerializers, streamType, false);
    }

    public XMPPLayer(
            PayloadParserFactoryCollection payloadParserFactories,
            PayloadSerializerCollection payloadSerializers,
            StreamType streamType,
            boolean setExplictNSonTopLevelElements) {
        payloadParserFactories_ = payloadParserFactories;
        payloadSerializers_ = payloadSerializers;
        setExplictNSonTopLevelElements_ = setExplictNSonTopLevelElements;
        resetParserAfterParse_ = false;
        inParser_ = false;
        xmppParser_ = new XMPPParser(this, payloadParserFactories_);
        xmppSerializer_ = new XMPPSerializer(payloadSerializers_, streamType, setExplictNSonTopLevelElements);
    }

    public void writeHeader(ProtocolHeader header) {
        writeDataInternal(new SafeByteArray(xmppSerializer_.serializeHeader(header)));
    }

    public void writeFooter() {
        writeDataInternal(new SafeByteArray(xmppSerializer_.serializeFooter()));
    }

    public void writeElement(Element element) {
        writeDataInternal(new SafeByteArray(xmppSerializer_.serializeElement(element)));
    }

    public void writeData(String data) {
        writeDataInternal(new SafeByteArray(data));
    }

    public void resetParser() {
        if (inParser_) {
            resetParserAfterParse_ = true;
        }
        else {
            doResetParser();
        }
    }

    /**
     * Should be protected, but can't because of interface implementation.
     * @param data
     */
    public void handleDataRead(SafeByteArray data) {
        handleDataReadInternal(data);
    }

    protected void writeDataInternal(SafeByteArray data) {
        onWriteData.emit(data);
        writeDataToChildLayer(data);
    }

    public void handleStreamStart(ProtocolHeader header) {
        onStreamStart.emit(header);
    }

    public void handleElement(Element element) {
        onElement.emit(element);
    }

    public void handleStreamEnd() {
    }

    private void doResetParser() {
        xmppParser_ = new XMPPParser(this, payloadParserFactories_);
        resetParserAfterParse_ = false;
    }
    
    /* Multiple-inheritance workarounds */

    private StreamLayer fakeStreamLayer_ = new StreamLayer() {
        public void writeData(SafeByteArray data) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void handleDataRead(SafeByteArray data) {
            handleDataReadInternal(data);
        }
    };

    private void handleDataReadInternal(SafeByteArray data) {
        onDataRead.emit(data);
        inParser_ = true;
        if(!xmppParser_.parse(data.toString())) {
            inParser_ = false;
            onError.emit();
            return;
        }
        inParser_ = false;
        if (resetParserAfterParse_) {
            doResetParser();
        }
    }

    public LowLayer getChildLayer() {
        return fakeStreamLayer_.getChildLayer();
    }

    public void setChildLayer(LowLayer childLayer) {
        fakeStreamLayer_.setChildLayer(childLayer);
    }

    public void writeDataToChildLayer(SafeByteArray data) {
        fakeStreamLayer_.writeDataToChildLayer(data);
    }
}
