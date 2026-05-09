class Graph {
    constructor(element_id, nodes, edges) {
        this.container = document.getElementById(element_id);
        this.vis_nodes = new vis.DataSet(nodes);
        this.vis_edges = new vis.DataSet(edges);
        this.data = {nodes: this.vis_nodes, edges: this.vis_edges};
        this.options = {
            "configure": {
                "enabled": false
            },
            "edges": {
                "color": {
                    "inherit": true
                },
                "smooth": {
                    "enabled": true,
                    "type": "dynamic"
                }
            },
            "interaction": {
                "dragNodes": true,
                "hideEdgesOnDrag": false,
                "hideNodesOnDrag": false
            },
            "physics": {
                "barnesHut": {
                    "avoidOverlap": 0,
                    "centralGravity": 0.3,
                    "damping": 0.09,
                    "gravitationalConstant": -80000,
                    "springConstant": 0.001,
                    "springLength": 250
                },
                "enabled": true,
                "repulsion": {
                    "centralGravity": 0.2,
                    "damping": 1,
                    "nodeDistance": 100,
                    "springConstant": 0.05,
                    "springLength": 200
                },
                "solver": "repulsion",
                "stabilization": {
                    "enabled": true,
                    "fit": true,
                    "iterations": 1000,
                    "onlyDynamicEdges": false,
                    "updateInterval": 50
                }
            }
        };
    }

    draw() {
        this.network = new vis.Network(this.container, this.data, this.options);
    }

    destroy() {
        this.network.destroy();
        this.container.innerHTML = "";
    }
}