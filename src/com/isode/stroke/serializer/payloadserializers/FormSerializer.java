/*
 * Copyright (c) 2012-2014 Isode Limited, London, England.
 * All rights reserved.
 */
/*
 * Copyright (c) 2010 Kevin Smith
 * All rights reserved.
 */

package com.isode.stroke.serializer.payloadserializers;

import com.isode.stroke.elements.Form;
import com.isode.stroke.elements.FormField;
import com.isode.stroke.elements.FormItem;
import com.isode.stroke.elements.FormSection;
import com.isode.stroke.elements.FormText;
import com.isode.stroke.elements.FormPage;
import com.isode.stroke.elements.FormReportedRef;
import com.isode.stroke.serializer.GenericPayloadSerializer;
import com.isode.stroke.serializer.xml.XMLElement;
import com.isode.stroke.serializer.xml.XMLTextNode;
import java.util.Vector;

/**
 * Serializer for {@link Form} element.
 */
public class FormSerializer extends GenericPayloadSerializer<Form> {

    private Vector<FormField> fields_ = new Vector<FormField>();

    public FormSerializer() {
        super(Form.class);
    }

    public String serializePayload(Form form) {
        if (form == null) {
            throw new NullPointerException("'form' must not be null");
        }
        
        XMLElement formElement = new XMLElement("x", "jabber:x:data");
        String type = form.getType().getStringForm();
        formElement.setAttribute(Form.FORM_ATTRIBUTE_TYPE, type);
        if (!form.getTitle().isEmpty()) {
            multiLineify(form.getTitle(), Form.FORM_ELEMENT_TITLE, formElement);
        }
        if (!form.getInstructions().isEmpty()) {
            multiLineify(form.getInstructions(),
                    Form.FORM_ELEMENT_INSTRUCTIONS, formElement);
        }
        
        // Add reported element

        for(FormPage page : form.getPages()) {
            formElement.addNode(pageToXML(page));
        }

        for(FormField field : form.getFields()) {
            formElement.addNode(fieldToXML(field, true));
        }
        
        if (!form.getReportedFields().isEmpty()) {
        	XMLElement reportedElement = new XMLElement("reported");
        	for (FormField field : form.getReportedFields()) {
            	reportedElement.addNode(fieldToXML(field, true));	
        	}
        	formElement.addNode(reportedElement);
        }
        
        // Add item elements
        for (FormItem item : form.getItems()) {
        	XMLElement itemElement = new XMLElement("item");
        	for (FormField ff : item.getItemFields()) {
        		itemElement.addNode(fieldToXML(ff, false));
        	}
        	formElement.addNode(itemElement);
        }

        for(FormText text : form.getTextElements()) {
            formElement.addNode(textToXML(text));
        }
        
        for(FormField field : fields_) {
            formElement.addNode(fieldToXML(field,true));
        }

        return formElement.serialize();
    }
    
    private XMLElement fieldToXML(FormField field, boolean withTypeAttribute) {
        if (field == null) {
            throw new NullPointerException("'field' must not be null");
        }

        XMLElement fieldElement = new XMLElement(Form.FORM_ELEMENT_FIELD);
        if (!field.getName().isEmpty()) {
            fieldElement.setAttribute(FormField.FORM_FIELD_ATTRIBUTE_VAR, field
                    .getName());
        }
        if (!field.getLabel().isEmpty()) {
            fieldElement.setAttribute(FormField.FORM_FIELD_ATTRIBUTE_LABEL,
                    field.getLabel());
        }
        if (field.getRequired()) {
            fieldElement.addNode(new XMLElement(
                    FormField.FORM_FIELD_ELEMENT_REQUIRED));
        }
        if (!field.getDescription().isEmpty()) {
            XMLElement descriptionElement = new XMLElement(
                    FormField.FORM_FIELD_ELEMENT_DESC);
            descriptionElement.addNode(new XMLTextNode(field.getDescription()));
            fieldElement.addNode(descriptionElement);
        }
        
        FormField.Type fieldType = field.getType();
        if (fieldType != FormField.Type.UNKNOWN_TYPE && withTypeAttribute) {
		fieldElement.setAttribute("type", fieldType.getDescription());
        }

        for (String s : field.getValues()) {
        	XMLElement valueElement = new XMLElement("value");
		valueElement.addNode(XMLTextNode.create(s));
        	fieldElement.addNode(valueElement);
        }
        
        for (FormField.Option option : field.getOptions()) {
        	XMLElement optionElement = new XMLElement("option");
        	if (!option.label_.isEmpty()) {
        		optionElement.setAttribute("label", option.label_);
        	}
        	XMLElement valueElement = new XMLElement("value");
        	valueElement.addNode(XMLTextNode.create(option.value_));
        	optionElement.addNode(valueElement);
        	fieldElement.addNode(optionElement);
        }
        return fieldElement;
    }

    private XMLElement textToXML(FormText text) {
        XMLElement textElement = new XMLElement("text");
        textElement.addNode(new XMLTextNode(text.getTextString()));
        return textElement;
    }

    private XMLElement fieldRefToXML(String ref) {
        XMLElement fieldRefElement = new XMLElement("fieldref");
        fieldRefElement.setAttribute("var", ref);
        return fieldRefElement;
    }

    /*private XMLElement reportedRefToXML(FormReportedRef reportedRef) {

    }*/

    private XMLElement pageToXML(FormPage page) {
        XMLElement pageElement = new XMLElement("page");
        pageElement.setAttribute("xmlns","http://jabber.org/protocol/xdata-layout");
        if (!page.getLabel().isEmpty()) {
            pageElement.setAttribute("label", page.getLabel());
        }
        for(FormText text : page.getTextElements()) {
            pageElement.addNode(textToXML(text));
        }
        for (FormField field : page.getFields()) {
            pageElement.addNode(fieldRefToXML(field.getName()));
            fields_.add(field);
        }
        for(FormReportedRef reportedRef : page.getReportedRefs()) {
            pageElement.addNode(new XMLElement("reportedref"));
        }
        for(FormSection section : page.getChildSections()) {
            pageElement.addNode(sectionToXML(section));
        }
        return pageElement;
    }

    private XMLElement sectionToXML(FormSection section) {
        XMLElement sectionElement = new XMLElement("section");
        if (!section.getLabel().isEmpty()) {
            sectionElement.setAttribute("label", section.getLabel());
        }
        for(FormText text : section.getTextElements()) {
            sectionElement.addNode(textToXML(text));
        }
        for(FormField field : section.getFields()) {
            sectionElement.addNode(fieldRefToXML(field.getName()));
            fields_.add(field);
        }
        for(FormReportedRef reportedRef : section.getReportedRefs()) {
            sectionElement.addNode(new XMLElement("reportedref"));
        }
        for(FormSection childSection : section.getChildSections()) {
            sectionElement.addNode(sectionToXML(childSection));
        }
        return sectionElement;
    }

    private void multiLineify(String text, String elementName,
            XMLElement element) {
        if (text == null) {
            throw new NullPointerException("'text' must not be null");
        }
        if (elementName == null) {
            throw new NullPointerException("'elementName' must not be null");
        }

        String unRdText = text.replaceAll("\r", "");
        String[] lines = unRdText.split("\n");
        for (String line : lines) {
            XMLElement lineElement = new XMLElement(elementName);
            lineElement.addNode(new XMLTextNode(line));
            element.addNode(lineElement);
        }
    }

    @Override
    public String toString() {
        return FormSerializer.class.getSimpleName();
    }
}
