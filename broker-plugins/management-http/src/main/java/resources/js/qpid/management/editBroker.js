/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
define(["dojox/html/entities",
        "dojo/_base/array",
        "dojo/_base/event",
        "dojo/_base/lang",
        "dojo/_base/window",
        "dojo/dom",
        "dojo/dom-construct",
        "dijit/registry",
        "dojo/parser",
        'dojo/json',
        "dojo/query",
        "dojo/store/Memory",
        "dojo/data/ObjectStore",
        "qpid/common/util",
        "dojo/text!editBroker.html",
        "qpid/common/ContextVariablesEditor",
        "dijit/Dialog",
        "dijit/form/CheckBox",
        "dijit/form/FilteringSelect",
        "dijit/form/ValidationTextBox",
        "dijit/form/Button",
        "dijit/form/Form",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"],
  function (entities, array, event, lang, win, dom, domConstruct, registry, parser, json, query, Memory, ObjectStore, util, template)
  {
    var fields = [ "name", "defaultVirtualHost", "statisticsReportingPeriod", "statisticsReportingResetEnabled", "connection.sessionCountLimit", "connection.heartBeatDelay"];
    var numericFieldNames = ["statisticsReportingPeriod", "connection.sessionCountLimit", "connection.heartBeatDelay"];

    var brokerEditor =
    {
      init: function()
      {
        var that=this;
        this.containerNode = domConstruct.create("div", {innerHTML: template});
        parser.parse(this.containerNode).then(function(instances){ that._postParse();});
      },
      _postParse: function()
      {
        var that=this;
        this.dialog = registry.byId("editBrokerDialog");
        this.saveButton = registry.byId("editBroker.saveButton");
        this.cancelButton = registry.byId("editBroker.cancelButton");
        this.cancelButton.on("click", function(e){that._cancel(e);});
        this.saveButton.on("click", function(e){that._save(e);});
        for(var i = 0; i < fields.length; i++)
        {
            var fieldName = fields[i];
            this[fieldName] = registry.byId("editBroker." + fieldName);
        }
        this.form = registry.byId("editBrokerForm");
        this.form.on("submit", function(){return false;});
        this.context = registry.byId("editBroker.context");

        for(var i = 0; i < numericFieldNames.length; i++)
        {
            this[numericFieldNames[i]].set("regExpGen", util.numericOrContextVarRegexp);
        }
      },
      show: function(management, brokerData)
      {
        this.management = management;
        var that=this;
        this.dialog.set("title", "Edit Broker - " + entities.encode(String(brokerData.name)));
        management.load( {type:"broker"}, { actuals: true }).then(
              function(data)
              {
                that._show(data[0], brokerData);
              });
      },
      destroy: function()
      {
        if (this.dialog)
        {
            this.dialog.destroyRecursive();
            this.dialog = null;
        }

        if (this.containerNode)
        {
            domConstruct.destroy(this.containerNode);
            this.containerNode = null;
        }
      },
      _cancel: function(e)
      {
          this.dialog.hide();
      },
      _save: function(e)
      {
          event.stop(e);
          if(this.form.validate())
          {
              var data = util.getFormWidgetValues(this.form, this.initialData);
              var context = this.context.get("value");
              if (context && !util.equals(context, this.initialData.context))
              {
                data["context"] = context;
              }

              var that = this;
              this.management.update({type: "broker"}, data).then(function(x){that.dialog.hide();});
          }
          else
          {
              alert('Form contains invalid data.  Please correct first');
          }
      },
      _show:function(actualData, effectiveData)
      {
          this.initialData = actualData;
          var nodes = effectiveData.virtualhostnodes
          var data = [];
          if (nodes)
          {
             for (var i=0; i< nodes.length; i++)
             {
                 if (nodes[i].virtualhosts)
                 {
                     data.push({id: nodes[i].virtualhosts[0].name, name: nodes[i].virtualhosts[0].name});
                 }
             }
          }
          var hostsStore = new dojo.store.Memory({ data: data });
          this["defaultVirtualHost"].set("store", hostsStore);


          for(var i = 0; i < fields.length; i++)
          {
            var fieldName = fields[i];
            var widget = this[fieldName];
            widget.reset();

            if (widget instanceof dijit.form.CheckBox)
            {
              widget.set("checked", actualData[fieldName]);
            }
            else
            {
              widget.set("value", actualData[fieldName]);
            }
          }
          util.applyMetadataToWidgets(dom.byId("editBroker.allFields"), "Broker", "broker", this.management.metadata);
          util.setContextData(this.context, management, {type: "broker"}, actualData, effectiveData );
          this.dialog.startup();
          this.dialog.show();
          if (!this.resizeEventRegistered)
          {
              this.resizeEventRegistered = true;
              util.resizeContentAreaAndRepositionDialog(dom.byId("editBroker.contentPane"), this.dialog);
          }
      }
    };

    brokerEditor.init();

    return brokerEditor;
  }
);
