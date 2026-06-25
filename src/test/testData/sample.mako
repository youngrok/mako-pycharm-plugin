## A single-line Mako comment
<%doc>
    A multi-line doc comment.
</%doc>
<%inherit file="base.mako"/>
<%page args="items, title='Untitled'"/>

<%! import datetime %>

<%def name="greeting(name)">
    <p>Hello, ${name | h}!</p>
</%def>

<html>
<body>
    <h1>${title}</h1>
    % if items:
        <ul>
        % for item in items:
            <li>${item}</li>
        % endfor
        </ul>
    % else:
        <p>No items.</p>
    % endif

    <% now = datetime.datetime.now() %>
    <p>Rendered at ${now}</p>
    <%text>Literal ${not_interpolated} text</%text>
</body>
</html>
