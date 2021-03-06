package com.talentica.rowingapp.common.data.stroke;

import com.talentica.rowingapp.common.param.ParamKeys;
import com.talentica.rowingapp.common.param.Parameter;
import com.talentica.rowingapp.common.param.ParameterService;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;

import static com.talentica.rowingapp.common.param.ParameterLevel.DEBUG;
import static com.talentica.rowingapp.common.param.ParameterLevel.PRIVATE;

public class ParamRegistration {
    private final LinkedList<Parameter> params = new LinkedList<Parameter>();

    private ParamRegistration() {
        for (ParamKeys info : ParamKeys.values()) {
            params.add(new Parameter(info));
        }
    }

    public void printParams(OutputStream os) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
        writer.write("<div class='robostroke-params'>\n" +
                "<table class='robostroke-param-table' border='1'>\n" +
                "<tr>" +
                "<th>Category</th><th>Name</th><th>Level</th><th>Default Value</th><th>Description</th>" +
                "</tr>\n");

        HashMap<String, LinkedList<Parameter>> paramGroups = new HashMap<String, LinkedList<Parameter>>();

        for (Parameter p : params) {
            switch (p.getLevel()) {
                case DEBUG:
                case PRIVATE:
                    break;
                default:
                    LinkedList<Parameter> group = paramGroups.get(p.getCategory());
                    if (group == null) {
                        group = new LinkedList<Parameter>();
                        paramGroups.put(p.getCategory(), group);
                    }
                    group.add(p);
                    break;
            }
        }

        for (Entry<String, LinkedList<Parameter>> e : paramGroups.entrySet()) {
            LinkedList<Parameter> paramGroup = e.getValue();
            int i = 0;
            for (Parameter param : paramGroup) {
                writer.write("<tr class='robostroke-param' id='" + param.getId() + "'>\n");

                if (i++ == 0) {
                    writer.write("<td class='robostroke-param-category' rowspan='" + paramGroup.size() + "'>" + param.getCategory() + "</td>\n");
                }

                writer.write("<td class='robostroke-param-name'>" + param.getName() + "</td>\n" +
                        "<td class='robostroke-param-level'>" + param.getLevel() + "</td>\n" +
                        "<td class='robostroke-param-default'>" + param.getDefaultValue() + "</td>\n" +
                        "<td class='robostroke-param-description'>" + param.getDescription() + "</td>\n");
                writer.write("</tr>\n");
            }
        }

        writer.write("</table></div>");
        writer.flush();
    }

    static void installParams(ParameterService ps) {
        ParamRegistration pr = new ParamRegistration();
        ps.registerParam(pr.params.toArray(new Parameter[pr.params.size()]));
    }

    public static void main(String[] args) throws IOException {
        new ParamRegistration().printParams(System.out);
    }

}
