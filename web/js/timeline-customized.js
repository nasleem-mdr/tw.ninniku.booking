var items;
var groups;
var options;
var timeline ;
var container;
var cStart;
  options = {
   zoomMin: 604800000,
   zoomMax: 3628800000,
   xss:{disabled: true},
   tooltip: {
      followMouse: false,
      overflowMethod: 'cap'
    },
    editable: true,
    onAdd: function (item, callback) {
      prettyPrompt('Add item', 'Enter text content for new item:', item.content, function (value) {
        if (value) {
          item.content = value;
          callback(item); // send back adjusted new item
        }
        else {
          callback(null); // cancel item creation
        }
      });
    },

    onMove: function (item, callback) {
  			zk.$("$dateStart").setValue(item.start.getTime().toString());
			zk.$("$dateEnd").setValue(item.end.getTime().toString());
			zk.$("$dateLast").setValue(Date.now().toString());
			zk.$("$group").setValue(item.group);
		   
			zk.$("$productionID").setValue(item.id.toString());
			
			cStart = item.start;
			zk.$("$group").fireOnChange();	
			zk.$("$dateEnd").fireOnChange();	
			zk.$("$dateStart").fireOnChange();
			zk.$("$productionID").fireOnChange();
			zk.$("$dateLast").fireOnChange();
	
    		  callback(item); 
    },

    onMoving: function (item, callback) {
      callback(item); // send back the (possibly) changed item
      
    },

    onUpdate: function (item, callback) {
    		$("#documentNo").val(item.documentNo);
    		$("#product").val(item.product);
    		$("#productionQty").val(item.productionQty);
    		$("#description").val(item.description);
    		$("#group").val(item.group);
    		$("#M_Production_ID").val(item.M_Production_ID);
    		$("#update-form").dialog({
    		        title: "修改製令單",
      				modal: true,
      				buttons: {
       				 Ok: function() {
         					 $( this ).dialog( "close" );
         					  item.description = $("#description").val();
         					  item.productionQty = $("#productionQty").val();
         					  item.group = $("#group").val();

       				 		const json = convertFormToJSON($("#production-form"));
							/**
							將Form 資料轉換成 json 讓後端處理
							 */
								         					          					  
         					  zk.$("$itemData").setValue(JSON.stringify(json));
							  zk.$("$itemData").fireOnChange();
         					  zk.$("$productionUpdated").setValue(Date.now().toString());
         					  zk.$("$productionUpdated").fireOnChange();
         					  
         					  callback(item);
         					  
       				 }, Cancel: function() {
       				 

       				 			
       				  		 $( this ).dialog( "close" );
         					 callback(null);
         					
       				 }
     				 }
   			 });
    },

    onRemove: function (item, callback) {
     callback(null); // cancel deletion
    }
  };

   function initChart() {

          container = document.getElementById("timeline-chart");
		  timeline = new vis.Timeline(container, items, groups, options);
		  initIPQC();
  }
   function drawChart() {

           timeline.setData({groups: groups,items: items});
           timeline.redraw();
           initIPQC();
  }
  
  function initIPQC(){
  				 $( ".vis-item-content>span>span" ).button();
		        $( ".vis-item-content>span>span" ).on("click",function(e){
			
				 console.log(e);
				 console.log("span click");
			    console.log($(this).attr("M_Production_ID"));

		 	}
		 );
  }
  function convertFormToJSON(form) {
  const array = $(form).serializeArray(); // Encodes the set of form elements as an array of names and values.
  const json = {};
  $.each(array, function () {
    json[this.name] = this.value || "";
  });
  return json;
}