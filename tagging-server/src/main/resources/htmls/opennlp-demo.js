//   Licensed to the Apache Software Foundation (ASF) under one or more
//   contributor license agreements.  See the NOTICE file distributed with
//   this work for additional information regarding copyright ownership.
//   The ASF licenses this file to You under the Apache License, Version 2.0
//   (the "License"); you may not use this file except in compliance with
//   the License.  You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

(function( $ ){
	
	function insertDemoElements( element ) {
		element.append('<select id="opennlp-demo-language"></select>');
		element.append('<div id="input-output">');
    	element.append('<textarea id="opennlp-demo-input"></textarea>');
    	element.append('<div id="opennlp-demo-result"></div>');
		element.append('</div>');    	
    	element.append('<button id="opennlp-demo-process">Process</button>');
	};
	
	function detectLanguage( componentName ) {
		
		// TODO:
		// Query name finder tagging service for supported languages
		// Try to auto detect language in the background (every time input changed)
		
		// For now lets hard code English
		$('select#opennlp-demo-language').append('<option value="en">English</option>');
	};
	
  var methods = {
    
    namefinder : function( options ) { 
    	insertDemoElements(this);
    	
    	// Can we get the method name here? 
    	detectLanguage('namefinder')
    	
    	$('button#opennlp-demo-process').click(function() {
    		
    		//$.ajax({url:"/rest/namefinder/_findRawText",type:"GET",data: "Test text",dataType:"json",contentType:"application/json"})
    		
    		var inputText = $('textarea#opennlp-demo-input').val();
    		
    		// Call the name finder service to detect names
			$.ajax({
				  type: 'POST',
				  url: "http://localhost:8080/rest/namefinder/_findRawText",
				  data: inputText,
				  dataType: "json",
				  contentType: "application/json; charset=UTF-8",
				  success: function (data) {
					
				    $("div#opennlp-demo-result").empty();
					
				    // Extract tokens and names from the array array
					var tokens = [];
					var annotations = [];

					for (si = 0; si < data.document.length; si++) {
						for (ni = 0; ni < data.names[si].length; ni++) {
							var ann = data.names[si][ni];
							
							// alert(data.names[si][ni]);
							
							annotations.push(new Annotation("person", 0, tokens.length + ann.start, 
									tokens.length + ann.end));
						}
						
						for (ti = 0; ti < data.document[si].length; ti++) {
							// TODO: its end and begin index, need to do substring on input text!
							
							tokens.push(inputText.substring(data.document[si][ti].start, data.document[si][ti].end));
						}
					}
				    
				    // Display results in annotation editor!
    				new AnnotationEditor($("div#opennlp-demo-result"),
							tokens, annotations);
    			  },
    			  error: function (jqXHR, textStatus, errorThrown) {
    			  	alert("Failed to call name finder service for some reason!");
    			  }
				});
    	});
    }
  };

  $.fn.opennlpDemo = function( method ) {
    
    // Method calling logic
    if ( methods[method] ) {
      return methods[ method ].apply( this, Array.prototype.slice.call( arguments, 1 ));
    } else if ( typeof method === 'object' || ! method ) {
      return methods.init.apply( this, arguments );
    } else {
      $.error( 'Method ' +  method + ' does not exist on jQuery.opennlpDemo' );
    }    
  
  };

})( jQuery );
