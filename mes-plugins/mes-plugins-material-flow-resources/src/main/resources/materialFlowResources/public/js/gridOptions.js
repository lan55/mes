
var myApp = angular.module('gridApp', []);

myApp.directive('ngJqGrid', function () {
    return {
        restrict: 'E',
        scope: {
            config: '=',
            data: '=',
        },
        link: function (scope, element, attrs) {
            var table;

            scope.$watch('config', function (newValue) {
                element.children().empty();
                table = angular.element('<table id="grid"></table>');
                element.append(table);
                element.append(angular.element('<div id="jqGridPager"></div>'));
                $(table).jqGrid(newValue);


                $(table).jqGrid('filterToolbar');

                $(table).navGrid('#jqGridPager',
                        // the buttons to appear on the toolbar of the grid
                                {edit: true, add: true, del: true, search: false, refresh: false, view: false, position: "left", cloneToTop: false},
                        // options for the Edit Dialog
                        {
                            ajaxEditOptions: {contentType: "application/json"},
                            mtype: 'PUT',
                            closeAfterEdit: true,
                            serializeEditData: function (data) {
                                delete data.oper;
                                return JSON.stringify(data);
                            },
                            onclickSubmit: function (params, postdata) {
                                params.url = '../../integration/rest/documentPositions/' + postdata.grid_id + ".html";
                            },
                            errorTextFormat: function (data) {
                                return 'Error: ' + data.responseText
                            }
                        },
                        // options for the Add Dialog
                        {
                            ajaxEditOptions: {contentType: "application/json"},
                            mtype: "POST",
                            closeAfterEdit: true,
                            serializeEditData: function (data) {
                                delete data.oper;
                                delete data.id;
                                return JSON.stringify(data);
                            },
                            onclickSubmit: function (params, postdata) {
                                params.url = '../../integration/rest/documentPositions.html';
                            },
                            errorTextFormat: function (data) {
                                return 'Error: ' + data.responseText
                            }
                        },
                        // options for the Delete Dailog
                        {
                            mtype: "DELETE",
                            serializeDelData: function () {
                                return ""; // don't send and body for the HTTP DELETE
                            },
                            onclickSubmit: function (params, postdata) {
                                params.url = '../../integration/rest/documentPositions/' + encodeURIComponent(postdata) + ".html";
                            },
                            errorTextFormat: function (data) {
                                return 'Error: ' + data.responseText
                            }
                        });
                    });

            scope.$watch('data', function (newValue, oldValue) {
                var i;
                for (i = oldValue.length - 1; i >= 0; i--) {
                    $(table).jqGrid('delRowData', i);
                }
                for (i = 0; i < newValue.length; i++) {
                    $(table).jqGrid('addRowData', i, newValue[i]);
                }
            });
        }
    };
});

myApp.controller('GridController', ['$scope', '$window', function ($scope, $window) {
        var _this = this;
        var lookupWindow;
        var productIdElement;

        var messagesController = new QCD.MessagesController();

        function showMessage(message) {
            messagesController.addMessage(message);
        }

        function getContextParamFromUrl() {
            var query = location.search.substr(1);
            var context = {};
            query.split("&").forEach(function (part) {
                var item = part.split("=");
                context[item[0]] = decodeURIComponent(item[1]);
            });

            context = JSON.parse(context.context);

            return context;
        }

        function validatePositive(value, column) {
            if (isNaN(value) && value < 0)
                return [false, "Please enter a positive value"];
            else
                return [true, ""];
        }

        this.onGridLinkClicked = function (entityId) {
            var grid = lookupWindow.mainController.getComponent("window.mainTab.grid");
//		var lookupData = grid.getLookupData(entityId);
            productIdElement.val(entityId);
            mainController.closeThisModalWindow();
        }

        function onModalClose() {
            lookupWindow = null;
        }

        function onModalRender(modalWindow) {
            modalWindow.getComponent("window.mainTab.grid").setLinkListener(_this);
        }

        function editProductId_openLookup() {
            lookupWindow = mainController.openModal('body', 'materialFlowResources/productsLookup.html', null, onModalClose, onModalRender, {width: 1000, height: 560})
        }

        function createLookupElement(inputId, value, url) {
            var $ac = $('<input id="' + inputId + '" class="eac-square"/>');
            $ac.val(value);
            $ac.autoComplete({
                source: function (query, response) {
                    try {
                        xhr.abort();
                    } catch (e) {
                    }
                    xhr = $.getJSON(url, {query: query}, function (data) {
                        response(data);
                    });
                },
                renderItem: function (item, search) {
                    var code = item.code;
                    var id = item.id;
                    // escape special characters
                    search = search.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
                    var re = new RegExp("(" + search.split(' ').join('|') + ")", "gi");
                    return '<div class="autocomplete-suggestion" data-id="' + id + '" data-val="' + code + '">' + code.replace(re, "<b>$1</b>") + '</div>';
                },
                onSelect: function (e, term, item) {
                    console.log(e)
                    console.log(term)
                    console.log(item)
                }
            });

//            var button = $('<button>Szukaj</button>');
//            button.bind('click', function () {
//                editProductId_openLookup();
//            });
//            button.insertAfter($ac);

            return $ac;
        }

        function palletNumbersLookup_createElement(value, options) {
            return createLookupElement('palletnumber', value, '/rest/palletnumbers');
        }

        function productsLookup_createElement(value, options) {
            return createLookupElement('square', value, '/rest/products');
        }

        function additionalCodeLookup_createElement(value, options) {
            return createLookupElement('additionalCode', value, '/rest/additionalcodes');
        }

        function lookup_value(elem, operation, value) {
            if (operation === 'get') {
                return $(elem).val();

            } else if (operation === 'set') {
                $('input', elem).val(value);
            }
        }
        function editProductId_createElement(value, options) {
            productIdElement = $('<input type="text" readonly="true" />');
            productIdElement.val(value);

            var button = productIdElement.after('<button>Produkt</button>');

            button.bind('click', function () {
                editProductId_openLookup();
            });

            return productIdElement;
        }

        function editProductId_value(elem, operation, value) {
            if (operation === 'get') {
                return $(elem).val();

            } else if (operation === 'set') {
                $('input', elem).val(value);
            }
        }

        function errorfunc(rowID, response) {
            showMessage({
                type: "failure",
                content: JSON.parse(response.responseText).message// QCD.translate("")
            });

            return true;
        }

        $scope.config = {
            url: '../../integration/rest/documentPositions/' + getContextParamFromUrl()['form.id'] + '.html',
            datatype: "json",
            height: '100%',
            autowidth: true,
            rowNum: 150,
            sortname: 'id',
            errorTextFormat: function (response) {
                console.log(response)
                
                return JSON.parse(response.responseText).message;
            },
            colNames: ['ID', 'product', 'additional_code', 'quantity', 'givenquantity', 'givenunit', 'conversion', 'expirationdate',
                'pallet', 'type_of_pallet'/*, 'storage_location_id', 'resource_id'*/],
            colModel: [
                {
                    name: 'id',
                    key: true,
                    width: 75,
                    hidden: true
                },
                {
                    name: 'product',
                    index: 'product',
                    editable: true,
                    edittype: 'custom',
                    editoptions: {
                        custom_element: productsLookup_createElement,
                        custom_value: lookup_value
                    }
                },
                {
                    name: 'additional_code',
                    index: 'additional_code',
                    editable: true,
                    edittype: 'custom',
                    editoptions: {
                        custom_element: additionalCodeLookup_createElement,
                        custom_value: lookup_value
                    }
                },
                {
                    name: 'quantity',
                    index: 'quantity',
                    editable: true,
                    formatter: 'number',
                    editrules: {
                        custom_func: validatePositive,
                        custom: true,
                        required: false
                    },
                },
                {
                    name: 'givenquantity',
                    index: 'givenquantity',
                    editable: true,
                    formatter: 'number',
                    editrules: {
                        custom_func: validatePositive,
                        custom: true,
                        required: false
                    },
                },
                {
                    name: 'givenunit',
                    index: 'givenunit',
                    editable: true,
                    edittype: 'select',
                    editoptions: {aysnc: false, dataUrl: '../../integration/rest/documentPositions/units.html',
                        buildSelect: function (response) {
                            var data = $.parseJSON(response);
                            var s = "<select>";

                            s += '<option value="0">--</option>';
                            $.each(data, function () {
                                s += '<option value="' + this.key + '">' + this.value + '</option>';
                            });

                            return s + "</select>";
                        }
                    },
                },
                {
                    name: 'conversion',
                    index: 'conversion',
                    editable: true,
                },
                {
                    name: 'expirationdate',
                    index: 'expirationdate',
                    width: 150,
                    editable: true,
                    edittype: "text",
                    editoptions: {
                        dataInit: function (element) {
                            var options = $.datepicker.regional[window.locale];
                            options.showOn = 'focus';

                            $(element).datepicker(options);
                        }
                    }
                },
                {
                    name: 'pallet',
                    index: 'pallet',
                    editable: true,
                    edittype: 'custom',
                    editoptions: {
                        custom_element: palletNumbersLookup_createElement,
                        custom_value: lookup_value
                    }
                },
                {
                    name: 'type_of_pallet',
                    index: 'type_of_pallet',
                    editable: true,
                    edittype: 'select',
                    editoptions: {aysnc: false, dataUrl: '../../integration/rest/documentPositions/typeOfPallets.html',
                        buildSelect: function (response) {
                            var data = $.parseJSON(response);
                            var s = "<select>";

                            s += '<option value="0">--</option>';
                            $.each(data, function () {
                                s += '<option value="' + this.key + '">' + this.value + '</option>';
                            });

                            return s + "</select>";
                        }
                    },
                }
                /*,
                 {
                 name: 'storage_location_id',
                 index: 'storage_location_id',
                 editable: true,
                 edittype: 'custom',
                 editoptions: {
                 // TODO
                 custom_element: editProductId_createElement,
                 custom_value: editProductId_value
                 }
                 },
                 {
                 name: 'resource_id',
                 index: 'resource_id',
                 editable: true,
                 edittype: 'custom',
                 editoptions: {
                 // TODO
                 custom_element: editProductId_createElement,
                 custom_value: editProductId_value
                 }
                 }*/
            ],
            pager: "#jqGridPager",
            onSelectRow: function (id) {
                jQuery('#grid').editRow(id, {
                    keys: true,
                    url: '../../integration/rest/documentPositions/' + id + ".html",
                    mtype: 'PUT',
                    errorfunc: errorfunc
                });
            },
            ajaxRowOptions: {contentType: "application/json"},
            serializeRowData: function (postdata) {
                delete postdata.oper;
                return JSON.stringify(postdata);
            }
        };

        $scope.data = [];

        var w = angular.element($window);
        function resize() {
            console.log('resize');
            jQuery('#grid').setGridWidth($("#window\\.positionsGridTab").width() - 20, true);
            jQuery('#grid').setGridHeight($("#window\\.positionsGridTab").height() - 200);
        }

        w.bind('resize', resize());
        resize();

        $scope.addNewRow = function () {
            jQuery('#grid').jqGrid("addRow", {errorfunc: errorfunc});
        }
    }]);
